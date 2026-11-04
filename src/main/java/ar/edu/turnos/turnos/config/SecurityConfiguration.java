package ar.edu.turnos.turnos.config;

import ar.edu.turnos.turnos.authorization.infrastructure.security.UserJwtSubject;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
public class SecurityConfiguration {
    private static final Set<String> CSRF_EXEMPT_POST_PATHS = Set.of(
            "/api/register", "/api/authenticate", "/api/reservation-processes");
    private static final String CONFIRM_PROCESS_PATH = "/api/reservation-processes/[^/]+/confirm";
    private static final String PHONE_PROCESS_PATH = "/api/reservation-processes/[^/]+/phone";

    @Bean
    SecretKey userJwtKey(@Value("${turnos.security.jwt-secret}") String secret) {
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) {
            throw new BeanCreationException("userJwtKey", "La clave HS256 debe tener al menos 32 bytes UTF-8");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey userJwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(userJwtKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey userJwtKey, @Value("${turnos.security.issuer}") String issuer) {
        var decoder = NimbusJwtDecoder.withSecretKey(userJwtKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<List<String>>("aud", audience -> audience != null
                        && audience.contains("backend-turnos")),
                new JwtClaimValidator<String>("sub", subject -> UserJwtSubject.userId(subject) != null)));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("authorities");
        authorities.setAuthorityPrefix("");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        // API sin sesión ni cookies de autenticación: el navegador no adjunta el JWT Bearer automáticamente.
        // Excluir únicamente los POST actuales; cualquier nuevo endpoint conserva CSRF por defecto.
        return http.csrf(csrf -> csrf.ignoringRequestMatchers(SecurityConfiguration::isCsrfExemptPost))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/register", "/api/authenticate").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/professionals/{id}/availability").hasAuthority("ROLE_USER")
                        .requestMatchers(HttpMethod.POST, "/api/reservation-processes").hasAuthority("ROLE_USER")
                         .requestMatchers(HttpMethod.POST, "/api/reservation-processes/*/confirm").hasAuthority("ROLE_USER")
                        .requestMatchers(HttpMethod.POST, "/api/reservation-processes/*/phone").hasAuthority("ROLE_USER")
                        .requestMatchers(HttpMethod.GET, "/api/reservation-processes/*").hasAuthority("ROLE_USER")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .build();
    }

    private static boolean isCsrfExemptPost(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return "POST".equals(request.getMethod()) && (CSRF_EXEMPT_POST_PATHS.contains(path)
                || path.matches(CONFIRM_PROCESS_PATH) || path.matches(PHONE_PROCESS_PATH));
    }
}

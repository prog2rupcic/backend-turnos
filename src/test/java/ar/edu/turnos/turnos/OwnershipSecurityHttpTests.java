package ar.edu.turnos.turnos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.turnos.turnos.authorization.domain.ports.in.EnsureResourceOwnerUseCase;
import ar.edu.turnos.turnos.authorization.infrastructure.security.UserJwtSubject;
import ar.edu.turnos.turnos.user.infrastructure.persistence.repository.JpaUserRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@Import(OwnershipSecurityHttpTests.OwnershipTestConfiguration.class)
class OwnershipSecurityHttpTests extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JpaUserRepository users;

    @Autowired
    private JwtEncoder jwtEncoder;

    private String ana;
    private String bruno;

    @BeforeEach
    void registerUsers() throws Exception {
        ana = register("ana");
        bruno = register("bruno");
    }

    @Test
    void ownerCanReadTheirOwnResource() throws Exception {
        mockMvc.perform(get("/__test/ownership/{login}", ana)
                        .header("Authorization", "Bearer " + login(ana)))
                .andExpect(status().isNoContent());
    }

    @Test
    void anotherUserCannotReadOrSpoofOwnership() throws Exception {
        Long anaId = users.findByLogin(ana).orElseThrow().getId();
        mockMvc.perform(get("/__test/ownership/{login}", ana)
                        .queryParam("userId", anaId.toString())
                        .header("Authorization", "Bearer " + login(bruno)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_OWNED"));
    }

    @Test
    void noTokenOrInvalidTokenCannotReadResource() throws Exception {
        mockMvc.perform(get("/__test/ownership/{login}", ana))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/__test/ownership/{login}", ana)
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAudienceExpiredTokenAndInvalidSubjectCannotReadResource() throws Exception {
        Long anaId = users.findByLogin(ana).orElseThrow().getId();
        Instant now = Instant.now();
        String wrongAudience = sign("user-" + anaId, List.of("backend-catalogo"), now, now.plusSeconds(3600), "ROLE_USER");
        String expired = sign("user-" + anaId, List.of("backend-turnos"), now.minusSeconds(7200),
                now.minusSeconds(3600), "ROLE_USER");
        String invalidSubject = sign("user-not-an-id", List.of("backend-turnos"), now,
                now.plusSeconds(3600), "ROLE_USER");
        for (String token : List.of(wrongAudience, expired, invalidSubject)) {
            mockMvc.perform(get("/__test/ownership/{login}", ana)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void roleWithoutUserPermissionCannotReadResource() throws Exception {
        Long anaId = users.findByLogin(ana).orElseThrow().getId();
        Instant now = Instant.now();
        String adminOnly = sign("user-" + anaId, List.of("backend-turnos"), now,
                now.plus(Duration.ofHours(1)), "ROLE_ADMIN");
        mockMvc.perform(get("/__test/ownership/{login}", ana)
                        .header("Authorization", "Bearer " + adminOnly))
                .andExpect(status().isForbidden());
    }

    @Test
    void userIdFromJwtMustBePositiveCanonicalLong() {
        assertThat(UserJwtSubject.userId("user-123")).isEqualTo(123L);
        for (String invalid : List.of("123", "user-0", "user--1", "user-001", "user-9223372036854775808")) {
            assertThat(UserJwtSubject.userId(invalid)).isNull();
        }
    }

    private String register(String prefix) throws Exception {
        String username = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"login":"%s","password":"clave-correcta","firstName":"Usuario",
                         "lastName":"Prueba","email":"%s@example.com"}
                        """.formatted(username, username)))
                .andExpect(status().isCreated());
        return username;
    }

    private String login(String username) throws Exception {
        var response = mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username":"%s","password":"clave-correcta"}
                        """.formatted(username)))
                .andExpect(status().isOk()).andReturn().getResponse();
        return JsonPath.read(response.getContentAsString(), "$.id_token");
    }

    private String sign(String subject, List<String> audience, Instant issuedAt, Instant expiresAt, String authority) {
        var claims = JwtClaimsSet.builder().issuer("backend-turnos").subject(subject).audience(audience)
                .issuedAt(issuedAt).expiresAt(expiresAt).claim("authorities", List.of(authority)).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OwnershipTestConfiguration {
        @Bean
        @Order(1)
        SecurityFilterChain ownershipTestFilter(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
            return http.securityMatcher("/__test/ownership/**")
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(requests -> requests.anyRequest().hasAuthority("ROLE_USER"))
                    .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                    .build();
        }

        @Bean
        TestOwnershipController testOwnershipController(EnsureResourceOwnerUseCase ownership, JpaUserRepository users) {
            return new TestOwnershipController(ownership, users);
        }
    }

    // Solo existe en pruebas: los controladores reales deberán obtener el propietario de su persistencia.
    @RestController
    static class TestOwnershipController {
        private final EnsureResourceOwnerUseCase ownership;
        private final JpaUserRepository users;

        TestOwnershipController(EnsureResourceOwnerUseCase ownership, JpaUserRepository users) {
            this.ownership = ownership;
            this.users = users;
        }

        @GetMapping("/__test/ownership/{login}")
        ResponseEntity<Void> read(@PathVariable String login) {
            Long ownerId = users.findByLogin(login)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)).getId();
            ownership.ensureOwner(ownerId);
            return ResponseEntity.noContent().build();
        }
    }
}

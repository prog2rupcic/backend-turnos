package ar.edu.turnos.turnos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.turnos.turnos.user.infrastructure.persistence.entity.UserEntity;
import ar.edu.turnos.turnos.user.infrastructure.persistence.repository.JpaUserRepository;
import ar.edu.turnos.turnos.user.infrastructure.security.Argon2PasswordHasher;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UserAuthenticationHttpTests extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JpaUserRepository users;

    @Autowired
    private Argon2PasswordHasher passwordHasher;

    private String login;

    @BeforeEach
    void registerUser() throws Exception {
        login = "ana-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"login":"%s","password":"clave-correcta","firstName":"Ana",
                                 "lastName":"Garcia","email":"%s@example.com"}
                                """.formatted(login, login)))
                .andExpect(status().isCreated());
    }

    @Test
    void issuesOneHourJwtCompatibleWithCatalog() throws Exception {
        var response = mockMvc.perform(post("/api/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(login, "clave-correcta", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id_token").isString())
                .andReturn().getResponse();

        String token = JsonPath.read(response.getContentAsString(), "$.id_token");
        assertThat(response.getHeader("Authorization")).isEqualTo("Bearer " + token);
        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("backend-turnos");
        assertThat(jwt.getSubject()).isEqualTo("user-" + users.findByLogin(login).orElseThrow().getId());
        assertThat(jwt.getAudience()).containsExactlyInAnyOrder("backend-turnos", "backend-catalogo");
        assertThat(jwt.getClaimAsStringList("authorities")).containsExactly("ROLE_USER");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void rememberMeTokenExpiresAfterSevenDays() throws Exception {
        String token = login("clave-correcta", true);
        Jwt jwt = jwtDecoder.decode(token);
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void omittedRememberMeDefaultsToOneHour() throws Exception {
        var response = mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"clave-correcta"}
                                """.formatted(login)))
                .andExpect(status().isOk()).andReturn().getResponse();
        String token = JsonPath.read(response.getContentAsString(), "$.id_token");
        Jwt jwt = jwtDecoder.decode(token);
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void invalidUsernameAndPasswordAreIndistinguishable() throws Exception {
        mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(login, "incorrecta", false)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials("unknown-user", "incorrecta", false)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void inactiveUserCannotLogIn() throws Exception {
        String inactiveLogin = "inactive-" + UUID.randomUUID().toString().substring(0, 8);
        Instant now = Instant.now();
        users.save(new UserEntity(null, inactiveLogin, passwordHasher.hash("clave-correcta"), "Ana", "Garcia",
                inactiveLogin + "@example.com", null, "es", false, Set.of("ROLE_USER"), now, now));

        mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(inactiveLogin, "clave-correcta", false)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void invalidRequestDoesNotExposePassword() throws Exception {
        mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields").isArray());
    }

    @Test
    void rejectsExpiredAndWrongAudienceTokens() throws Exception {
        Instant now = Instant.now();
        String expired = signToken(now.minus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)),
                "backend-turnos", List.of("backend-turnos"));
        String wrongAudience = signToken(now, now.plus(Duration.ofHours(1)),
                "backend-turnos", List.of("other-service"));
        String wrongIssuer = signToken(now, now.plus(Duration.ofHours(1)),
                "other-issuer", List.of("backend-turnos"));

        assertThatThrownBy(() -> jwtDecoder.decode(expired)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwtDecoder.decode(wrongAudience)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> jwtDecoder.decode(wrongIssuer)).isInstanceOf(JwtException.class);
        mockMvc.perform(get("/api/private").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyRegistrationAndLoginArePublic() throws Exception {
        mockMvc.perform(get("/api/private"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/private").header("Authorization", "Bearer " + login("clave-correcta", false)))
                .andExpect(status().isForbidden());
    }

    private String login(String password, boolean rememberMe) throws Exception {
        var response = mockMvc.perform(post("/api/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(login, password, rememberMe)))
                .andExpect(status().isOk()).andReturn().getResponse();
        return JsonPath.read(response.getContentAsString(), "$.id_token");
    }

    private String signToken(Instant issuedAt, Instant expiresAt, String issuer, List<String> audience) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject("user-1").audience(audience)
                .issuedAt(issuedAt).expiresAt(expiresAt).claim("authorities", List.of("ROLE_USER")).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static String credentials(String username, String password, boolean rememberMe) {
        return """
                {"username":"%s","password":"%s","rememberMe":%s}
                """.formatted(username, password, rememberMe);
    }
}

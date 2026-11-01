package ar.edu.turnos.turnos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.turnos.turnos.user.infrastructure.persistence.repository.JpaUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UserRegistrationHttpTests extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JpaUserRepository userRepository;

    @Test
    void registersUserWithoutReturningPassword() throws Exception {
        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "login": "ana.garcia",
                                  "password": "una-clave-segura",
                                  "firstName": "Ana",
                                  "lastName": "Garcia",
                                  "email": "ana.garcia@example.com",
                                  "langKey": "es"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.login").value("ana.garcia"))
                .andExpect(jsonPath("$.activated").value(true))
                .andExpect(jsonPath("$.authorities[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        var saved = userRepository.findByLogin("ana.garcia").orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getPasswordHash())
                .startsWith("$argon2id$")
                .isNotEqualTo("una-clave-segura");
    }

    @Test
    void rejectsDuplicateLogin() throws Exception {
        register("duplicated", "first@example.com");

        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson("duplicated", "second@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
    }

    @Test
    void rejectsInvalidRequest() throws Exception {
        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"a\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    private void register(String login, String email) throws Exception {
        mockMvc.perform(post("/api/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson(login, email)))
                .andExpect(status().isCreated());
    }

    private String registrationJson(String login, String email) {
        return """
                {
                  "login": "%s",
                  "password": "una-clave-segura",
                  "firstName": "Usuario",
                  "lastName": "Prueba",
                  "email": "%s",
                  "langKey": "es"
                }
                """.formatted(login, email);
    }
}

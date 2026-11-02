package ar.edu.turnos.turnos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AvailabilityHttpTests extends AbstractIntegrationTest {
    private static final HttpServer CATALOG = server();
    private static final HttpServer CATEDRA = server();
    private static final AtomicReference<Reply> CATALOG_REPLY = new AtomicReference<>();
    private static final AtomicReference<Reply> CATEDRA_REPLY = new AtomicReference<>();
    private static final AtomicReference<String> CATALOG_AUTH = new AtomicReference<>();
    private static final AtomicReference<String> CATEDRA_AUTH = new AtomicReference<>();
    private static final AtomicReference<String> CATEDRA_QUERY = new AtomicReference<>();
    private static final AtomicInteger CATEDRA_REQUESTS = new AtomicInteger();

    static {
        CATALOG.createContext("/api/professionals/", request -> reply(request, CATALOG_REPLY, CATALOG_AUTH, null));
        CATEDRA.createContext("/api/appointment-occupancies", request ->
                reply(request, CATEDRA_REPLY, CATEDRA_AUTH, CATEDRA_QUERY));
        CATALOG.start();
        CATEDRA.start();
    }

    @DynamicPropertySource
    static void remoteProperties(DynamicPropertyRegistry registry) {
        registry.add("turnos.catalog.base-url", () -> "http://localhost:" + CATALOG.getAddress().getPort());
        registry.add("turnos.catedra.base-url", () -> "http://localhost:" + CATEDRA.getAddress().getPort());
        registry.add("turnos.catedra.technical-jwt", () -> "test-only-technical-jwt");
    }

    @AfterAll
    static void stopServers() {
        CATALOG.stop(0);
        CATEDRA.stop(0);
    }

    @Autowired
    private MockMvc mockMvc;

    private LocalDate date;
    private String userJwt;

    @BeforeEach
    void prepare() throws Exception {
        date = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(7);
        CATALOG_REPLY.set(new Reply(200, agenda(true, true)));
        CATEDRA_REPLY.set(new Reply(200, occupancies("")));
        CATEDRA_REQUESTS.set(0);
        CATALOG_AUTH.set(null);
        CATEDRA_AUTH.set(null);
        CATEDRA_QUERY.set(null);

        String username = "availability-" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"login":"%s","password":"clave-correcta","firstName":"Ana",
                         "lastName":"Garcia","email":"%s@example.com"}
                        """.formatted(username, username))).andExpect(status().isCreated());
        var response = mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"clave-correcta"}
                        """.formatted(username))).andExpect(status().isOk()).andReturn().getResponse();
        userJwt = JsonPath.read(response.getContentAsString(), "$.id_token");
    }

    @Test
    void combinesAgendaAndOccupanciesWithSeparateJwtCredentials() throws Exception {
        CATEDRA_REPLY.set(new Reply(200, occupancies("""
                {"date":"%s","startTime":"09:30:00","status":"HELD","expiresAt":"2026-11-02T10:00:00Z"},
                {"date":"%s","startTime":"10:00","status":"CONFIRMED","expiresAt":null}
                """.formatted(date, date))));
        var call = mockMvc.perform(availability().header("Authorization", "Bearer " + userJwt));
        assertThat(CATALOG_AUTH.get()).isNotNull();
        call
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionalId").value(15))
                .andExpect(jsonPath("$.date").value(date.toString()))
                .andExpect(jsonPath("$.availableStartTimes.length()").value(2))
                .andExpect(jsonPath("$.availableStartTimes[0]").value("09:00:00"))
                .andExpect(jsonPath("$.availableStartTimes[1]").value("10:30:00"));

        assertThat(CATALOG_AUTH.get()).isEqualTo("Bearer " + userJwt);
        assertThat(CATEDRA_AUTH.get()).isEqualTo("Bearer test-only-technical-jwt");
        assertThat(CATEDRA_QUERY.get()).contains("professionalId=15", "from=" + date, "to=" + date);
    }

    @Test
    void emptyOccupanciesKeepAllScheduleSlots() throws Exception {
        mockMvc.perform(availability().header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableStartTimes.length()").value(4));
    }

    @Test
    void disabledProfessionalDoesNotNeedCentralOccupancies() throws Exception {
        CATALOG_REPLY.set(new Reply(200, agenda(false, true)));
        mockMvc.perform(availability().header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableStartTimes.length()").value(0));
        assertThat(CATEDRA_REQUESTS.get()).isZero();
    }

    @Test
    void unknownProfessionalAndUnavailableCatalogFailClosed() throws Exception {
        CATALOG_REPLY.set(new Reply(404, "{}"));
        mockMvc.perform(availability().header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isNotFound());
        CATALOG_REPLY.set(new Reply(503, "{}"));
        mockMvc.perform(availability().header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isServiceUnavailable());
        CATALOG_REPLY.set(new Reply(401, "{}"));
        mockMvc.perform(availability().header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isServiceUnavailable());
        assertThat(CATEDRA_REQUESTS.get()).isZero();
    }

    @Test
    void centralAuthenticationAndServerErrorsDoNotClaimFreeSlots() throws Exception {
        CATEDRA_REPLY.set(new Reply(401, "{}"));
        mockMvc.perform(availability().header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isServiceUnavailable());
        CATEDRA_REPLY.set(new Reply(500, "{}"));
        mockMvc.perform(availability().header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void malformedCentralDataFailsClosed() throws Exception {
        CATEDRA_REPLY.set(new Reply(200, "{\"occupancies\":[]}"));
        mockMvc.perform(availability().header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isBadGateway());
        CATEDRA_REPLY.set(new Reply(200, occupancies("""
                {"date":"%s","startTime":"09:00","status":"UNRECOGNIZED"}
                """.formatted(date))));
        mockMvc.perform(availability().header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isBadGateway());
    }

    @Test
    void invalidDateAndUnauthorizedRequestsNeverCallSources() throws Exception {
        mockMvc.perform(availability()).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/professionals/0/availability").param("date", date.toString())
                        .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/professionals/15/availability")
                        .param("date", date.minusDays(8).toString())
                        .header("Authorization", "Bearer " + userJwt))
                .andExpect(status().isBadRequest());
        assertThat(CATALOG_AUTH.get()).isNull();
        assertThat(CATEDRA_REQUESTS.get()).isZero();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder availability() {
        return get("/api/professionals/15/availability").param("date", date.toString());
    }

    private String agenda(boolean enabled, boolean categoryEnabled) {
        return """
                {"id":15,"firstName":"Ana","lastName":"Gomez","enabled":%s,
                 "category":{"id":2,"name":"Clínica","enabled":%s},"weeklySchedules":[
                 {"id":1,"dayOfWeek":"%s","startTime":"09:00:00","endTime":"11:00:00",
                  "slotDurationMinutes":30}]}
                """.formatted(enabled, categoryEnabled, date.getDayOfWeek());
    }

    private String occupancies(String items) {
        return """
                {"professionalId":15,"from":"%s","to":"%s","occupancies":[%s]}
                """.formatted(date, date, items);
    }

    private static HttpServer server() {
        try {
            return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo iniciar el servidor de pruebas", exception);
        }
    }

    private static void reply(HttpExchange request, AtomicReference<Reply> reply,
                              AtomicReference<String> authorization, AtomicReference<String> query) throws IOException {
        authorization.set(request.getRequestHeaders().getFirst("Authorization"));
        if (query != null) {
            query.set(request.getRequestURI().getRawQuery());
            CATEDRA_REQUESTS.incrementAndGet();
        }
        Reply answer = reply.get();
        byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type", "application/json");
        request.sendResponseHeaders(answer.status(), body.length);
        try (var output = request.getResponseBody()) {
            output.write(body);
        }
    }

    private record Reply(int status, String body) {
    }
}

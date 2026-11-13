package ar.edu.turnos.turnos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.turnos.turnos.reservation.domain.model.CancellationNotice;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.ports.in.RecordCancellationUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import ar.edu.turnos.turnos.reservation.infrastructure.persistence.repository.JpaReservationProcessRepository;
import ar.edu.turnos.turnos.user.infrastructure.persistence.entity.UserEntity;
import ar.edu.turnos.turnos.user.infrastructure.persistence.repository.JpaUserRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OwnReservationsHttpTests extends AbstractIntegrationTest {
    private static final HttpServer COURSE = server();
    private static final AtomicReference<String> LIST_REPLY = new AtomicReference<>();
    private static final AtomicReference<String> CANCELLATION_MODE = new AtomicReference<>("ok");
    private static final AtomicReference<String> LIST_QUERY = new AtomicReference<>();
    private static final AtomicReference<String> CANCEL_BODY = new AtomicReference<>();
    private static final AtomicReference<String> CANCEL_AUTH = new AtomicReference<>();
    private static final AtomicInteger CANCEL_CALLS = new AtomicInteger();
    private static final AtomicReference<Runnable> BEFORE_REPLY = new AtomicReference<>();

    static {
        COURSE.createContext("/api/appointments", request -> {
            if (request.getRequestURI().getPath().endsWith("/cancel")) {
                CANCEL_CALLS.incrementAndGet();
                CANCEL_BODY.set(new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                CANCEL_AUTH.set(request.getRequestHeaders().getFirst("Authorization"));
                var callback = BEFORE_REPLY.getAndSet(null);
                if (callback != null) { callback.run(); }
                switch (CANCELLATION_MODE.get()) {
                    case "unauthorized" -> send(request, 401, "{}");
                    case "unavailable" -> send(request, 503, "{}");
                    case "invalid" -> send(request, 200, "{\"status\":\"CONFIRMED\"}");
                    default -> send(request, 200, """
                            {"reservationId":8251,"reservationProcessId":"process-own",
                             "status":"CANCELLED","cancelledAt":"2026-11-10T14:00:00Z"}
                            """);
                }
            } else {
                LIST_QUERY.set(request.getRequestURI().getRawQuery());
                if ("unavailable".equals(LIST_REPLY.get())) { send(request, 503, "{}"); return; }
                if ("invalid".equals(LIST_REPLY.get())) { send(request, 200, "not-json"); return; }
                byte[] payload = LIST_REPLY.get().getBytes(StandardCharsets.UTF_8);
                request.getResponseHeaders().set("X-Total-Count", "3");
                request.getResponseHeaders().set("Content-Type", "application/json");
                request.sendResponseHeaders(200, payload.length);
                request.getResponseBody().write(payload);
                request.close();
            }
        });
        COURSE.start();
    }

    @DynamicPropertySource
    static void remote(DynamicPropertyRegistry registry) {
        registry.add("turnos.catedra.base-url", () -> "http://localhost:" + COURSE.getAddress().getPort());
        registry.add("turnos.catedra.technical-jwt", () -> "only-for-test-technical-jwt");
    }

    @AfterAll
    static void shutdown() { COURSE.stop(0); }

    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @Autowired JpaUserRepository users;
    @Autowired JpaReservationProcessRepository entities;
    @Autowired ReservationProcessRepository processes;
    @Autowired RecordCancellationUseCase cancellationEvent;
    private long owner;
    private long other;
    private String id;

    @BeforeEach
    void setUp() {
        entities.deleteAll();
        users.deleteAll();
        owner = user();
        other = user();
        id = UUID.randomUUID().toString();
        process(id, owner, "process-own", 8251);
        process(UUID.randomUUID().toString(), other, "process-foreign", 8252);
        LIST_REPLY.set("[" + appointment("process-own", "user-" + owner, 8251) + ","
                + appointment("process-foreign", "user-" + owner, 8252) + ","
                + appointment("process-orphan", "user-" + owner, 8253) + "]");
        CANCELLATION_MODE.set("ok");
        BEFORE_REPLY.set(null);
        CANCEL_CALLS.set(0);
    }

    @Test
    void listsOnlyLocallyOwnedReservationsWithCorrectPaginationAndWithoutPhone() throws Exception {
        mvc.perform(get("/api/reservations?page=0&size=1").header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].processId").value(id))
                .andExpect(jsonPath("$.content[0].patientPhone").doesNotExist());
        assertThat(LIST_QUERY.get()).contains("externalPatientId=user-" + owner, "page=0", "size=100");
        mvc.perform(get("/api/reservations?page=1&size=1").header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(get("/api/reservations?page=-1").header("Authorization", token(owner)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listingFailsClosedWhenCourseUnavailableOrResponseInvalid() throws Exception {
        LIST_REPLY.set("unavailable");
        mvc.perform(get("/api/reservations").header("Authorization", token(owner)))
                .andExpect(status().isServiceUnavailable());
        LIST_REPLY.set("invalid");
        mvc.perform(get("/api/reservations").header("Authorization", token(owner)))
                .andExpect(status().isBadGateway());
    }

    @Test
    void cancellationChecksOwnerAndPermissionBeforeCallingCourse() throws Exception {
        mvc.perform(get("/api/reservations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/reservations/{id}/cancel", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reservations").header("Authorization", token(owner, "ROLE_ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/reservations/{id}/cancel", id).header("Authorization", token(other)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/reservations/missing/cancel").header("Authorization", token(owner)))
                .andExpect(status().isNotFound());
        assertThat(CANCEL_CALLS.get()).isZero();
    }

    @Test
    void cancellationPersistsResultAndRepeatsWithoutAnotherExternalEffect() throws Exception {
        mvc.perform(post("/api/reservations/{id}/cancel", id).header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Cambio de plan\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Cambio de plan"));
        assertThat(CANCEL_BODY.get()).contains("Cambio de plan");
        assertThat(CANCEL_AUTH.get()).isEqualTo("Bearer only-for-test-technical-jwt");
        assertThat(processes.findById(id).orElseThrow().cancelledAt()).isNotNull();
        mvc.perform(post("/api/reservations/{id}/cancel", id).header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(CANCEL_CALLS.get()).isEqualTo(1);
    }

    @Test
    void unknownOutcomeIsPersistedAndNoEffectIsAutomaticallyRepeated() throws Exception {
        CANCELLATION_MODE.set("unavailable");
        mvc.perform(post("/api/reservations/{id}/cancel", id).header("Authorization", token(owner)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("OUTCOME_UNKNOWN"));
        assertThat(processes.findById(id).orElseThrow().status())
                .isEqualTo(ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN);
        mvc.perform(post("/api/reservations/{id}/cancel", id).header("Authorization", token(owner)))
                .andExpect(status().isConflict());
        assertThat(CANCEL_CALLS.get()).isEqualTo(1);
    }

    @Test
    void malformedCancellationResponseRemainsUncertain() throws Exception {
        CANCELLATION_MODE.set("invalid");
        mvc.perform(post("/api/reservations/{id}/cancel", id).header("Authorization", token(owner)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("OUTCOME_UNKNOWN"));
        assertThat(processes.findById(id).orElseThrow().status())
                .isEqualTo(ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN);
    }

    @Test
    void reasonCannotExceedFiveHundredCharacters() throws Exception {
        String longReason = "x".repeat(501);
        mvc.perform(post("/api/reservations/{id}/cancel", id).header("Authorization", token(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + longReason + "\"}"))
                .andExpect(status().isBadRequest());
        assertThat(CANCEL_CALLS.get()).isZero();
    }

    @Test
    void invalidTechnicalTokenDoesNotInvalidateTheEndUser() throws Exception {
        CANCELLATION_MODE.set("unauthorized");
        mvc.perform(post("/api/reservations/{id}/cancel", id).header("Authorization", token(owner)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INTEGRATION_UNAVAILABLE"));
        assertThat(processes.findById(id).orElseThrow().status()).isEqualTo(ReservationProcess.Status.CONFIRMED);
    }

    @Test
    void kafkaEventCanWinTheRaceAgainstTheRestResponse() throws Exception {
        BEFORE_REPLY.set(() -> cancellationEvent.record(new CancellationNotice("evt-" + UUID.randomUUID(),
                "process-own", "hold-own", "request-own", 8251, 15, LocalDate.of(2026, 11, 14),
                LocalTime.of(9, 30), "Desde cátedra", Instant.parse("2026-11-10T13:00:00Z"))));
        mvc.perform(post("/api/reservations/{id}/cancel", id).header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(processes.findById(id).orElseThrow().cancellationReason()).isEqualTo("Desde cátedra");
        assertThat(processes.findById(id).orElseThrow().cancelledAt())
                .isEqualTo(Instant.parse("2026-11-10T13:00:00Z"));
    }

    private long user() {
        return users.save(new UserEntity(null, "u-" + UUID.randomUUID(), "{argon2}not-used", "Ana", "Perez",
                UUID.randomUUID() + "@example.com", null, "es", true, Set.of("ROLE_USER"),
                Instant.now(), Instant.now())).getId();
    }

    private void process(String localId, long userId, String externalId, long reservationId) {
        var now = Instant.now();
        processes.save(new ReservationProcess(localId, userId, 15, LocalDate.of(2026, 11, 14),
                LocalTime.of(9, 30), ReservationProcess.Status.HELD,
                "process-own".equals(externalId) ? "hold-own" : "hold-foreign", externalId,
                now.plusSeconds(600), "process-own".equals(externalId) ? "request-own" : "request-foreign",
                "PHONE_NUMBER", "Teléfono", now, now, null)
                .confirmed(reservationId, now, now));
    }

    private static String appointment(String externalId, String patientId, long reservationId) {
        return """
                {"reservationId":%d,"reservationProcessId":"%s","externalPatientId":"%s",
                 "appointmentDate":"2026-11-14","startTime":"09:30:00","endTime":"10:00:00",
                 "professionalId":15,"professionalFirstName":"Ana","professionalLastName":"Gomez",
                 "patientPhone":"+5492615551234","status":"CONFIRMED",
                 "confirmedAt":"2026-11-10T12:00:00Z","otherField":"ignored"}
                """.formatted(reservationId, externalId, patientId);
    }

    private String token(long userId) { return token(userId, "ROLE_USER"); }

    private String token(long userId, String role) {
        var claims = JwtClaimsSet.builder().issuer("backend-turnos").subject("user-" + userId)
                .audience(List.of("backend-turnos", "backend-catalogo")).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600)).claim("authorities", List.of(role)).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static HttpServer server() {
        try { return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); }
        catch (IOException exception) { throw new ExceptionInInitializerError(exception); }
    }
}

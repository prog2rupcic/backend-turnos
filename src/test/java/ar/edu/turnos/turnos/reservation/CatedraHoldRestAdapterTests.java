package ar.edu.turnos.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.turnos.turnos.reservation.application.exception.HoldConfirmationException;
import ar.edu.turnos.turnos.reservation.application.exception.HoldCreationException;
import ar.edu.turnos.turnos.reservation.domain.model.ConfirmedHold;
import ar.edu.turnos.turnos.reservation.infrastructure.rest.CatedraHoldRestAdapter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class CatedraHoldRestAdapterTests {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 9);
    private static final LocalTime SLOT = LocalTime.of(9, 30);

    @Test
    void missingTechnicalJwtIsConfigurationFailureWithoutAnExternalPost() {
        var gateway = new CatedraHoldRestAdapter(RestClient.builder(), "http://127.0.0.1:1", " ", Clock.systemUTC());

        assertThatThrownBy(() -> gateway.create(15, DATE, SLOT, "user-42"))
                .isInstanceOfSatisfying(HoldCreationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(HoldCreationException.Reason.INTEGRATION_UNAVAILABLE));
    }

    @Test
    void lostConnectionAfterStartingPostHasUnknownOutcome() {
        var gateway = new CatedraHoldRestAdapter(RestClient.builder(), "http://127.0.0.1:1",
                "test-only-technical-jwt", Clock.systemUTC());

        assertThatThrownBy(() -> gateway.create(15, DATE, SLOT, "user-42"))
                .isInstanceOfSatisfying(HoldCreationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(HoldCreationException.Reason.OUTCOME_UNKNOWN));
    }

    @Test
    void missingTechnicalJwtPreventsHoldConfirmation() {
        var gateway = new CatedraHoldRestAdapter(RestClient.builder(), "http://127.0.0.1:1", " ", Clock.systemUTC());

        assertConfirmationReason(() -> gateway.confirm("hold-id", "user-42", "Ana", "Garcia"),
                HoldConfirmationException.Reason.INTEGRATION_UNAVAILABLE);
    }

    @Test
    void confirmsHoldUsingTechnicalJwtAndContractPayload() throws Exception {
        try (var remote = remote(new Reply(202, """
                {"reservationProcessId":"process-id","status":"WAITING_FOR_PHONE","expiresAt":"%s"}
                """.formatted(Instant.parse("2026-11-03T18:00:00Z"))))) {
            var result = adapter(remote).confirm("hold-id", "user-42", "Ana", "Garcia");

            assertThat(result).isEqualTo(new ConfirmedHold("process-id", Instant.parse("2026-11-03T18:00:00Z")));
            assertThat(remote.method.get()).isEqualTo("POST");
            assertThat(remote.path.get()).isEqualTo("/api/appointment-holds/hold-id/confirm");
            assertThat(remote.authorization.get()).isEqualTo("Bearer test-only-technical-jwt");
            assertThat(remote.body.get()).contains("\"externalPatientId\":\"user-42\"",
                    "\"patientFirstName\":\"Ana\"", "\"patientLastName\":\"Garcia\"");
        }
    }

    @Test
    void malformedSuccessfulConfirmationResponsesHaveUnknownOutcome() throws Exception {
        List<Reply> malformed = List.of(
                new Reply(200, """
                        {"reservationProcessId":"process-id","status":"WAITING_FOR_PHONE","expiresAt":"2026-11-03T18:00:00Z"}
                        """),
                new Reply(202, ""),
                new Reply(202, """
                        {"status":"WAITING_FOR_PHONE","expiresAt":"2026-11-03T18:00:00Z"}
                        """),
                new Reply(202, """
                        {"reservationProcessId":" ","status":"WAITING_FOR_PHONE","expiresAt":"2026-11-03T18:00:00Z"}
                        """),
                new Reply(202, """
                        {"reservationProcessId":"process-id","status":"HELD","expiresAt":"2026-11-03T18:00:00Z"}
                        """),
                new Reply(202, """
                        {"reservationProcessId":"process-id","status":"WAITING_FOR_PHONE"}
                        """));
        try (var remote = remote(malformed.getFirst())) {
            var gateway = adapter(remote);
            for (Reply reply : malformed) {
                remote.reply.set(reply);
                assertConfirmationReason(() -> gateway.confirm("hold-id", "user-42", "Ana", "Garcia"),
                        HoldConfirmationException.Reason.OUTCOME_UNKNOWN);
            }
        }
    }

    @Test
    void confirmationErrorsAreTranslatedByHttpStatusAndStableCode() throws Exception {
        List<Mapping> mappings = List.of(
                new Mapping(401, "{}", HoldConfirmationException.Reason.INTEGRATION_UNAVAILABLE),
                new Mapping(403, "{\"code\":\"HOLD_OWNER_MISMATCH\"}",
                        HoldConfirmationException.Reason.INTEGRATION_UNAVAILABLE),
                new Mapping(404, "{\"code\":\"HOLD_EXPIRED\"}", HoldConfirmationException.Reason.HOLD_EXPIRED),
                new Mapping(404, "{\"code\":\"HOLD_NOT_FOUND\"}", HoldConfirmationException.Reason.HOLD_NOT_FOUND),
                new Mapping(404, "{\"code\":\"PROFESSIONAL_NOT_FOUND\"}",
                        HoldConfirmationException.Reason.PROFESSIONAL_NOT_FOUND),
                new Mapping(404, "{\"code\":\"UNEXPECTED_NOT_FOUND\"}",
                        HoldConfirmationException.Reason.INVALID_RESPONSE),
                new Mapping(400, "{\"code\":\"INVALID_SLOT\"}", HoldConfirmationException.Reason.SLOT_UNAVAILABLE),
                new Mapping(409, "{\"code\":\"RESERVATION_PROCESS_ALREADY_CONFIRMED\"}",
                        HoldConfirmationException.Reason.ALREADY_CONFIRMED),
                new Mapping(409, "{\"code\":\"SLOT_ALREADY_HELD\"}", HoldConfirmationException.Reason.SLOT_UNAVAILABLE),
                new Mapping(409, "{\"code\":\"SLOT_ALREADY_RESERVED\"}", HoldConfirmationException.Reason.SLOT_UNAVAILABLE),
                new Mapping(409, "{\"code\":\"UNEXPECTED_CONFLICT\"}",
                        HoldConfirmationException.Reason.INVALID_RESPONSE),
                new Mapping(500, "{}", HoldConfirmationException.Reason.OUTCOME_UNKNOWN),
                new Mapping(400, "{\"code\":\"EXTERNAL_PATIENT_MISMATCH\"}",
                        HoldConfirmationException.Reason.INVALID_RESPONSE),
                new Mapping(422, "malformed error body", HoldConfirmationException.Reason.INVALID_RESPONSE));
        try (var remote = remote(new Reply(500, "{}"))) {
            var gateway = adapter(remote);
            for (Mapping mapping : mappings) {
                remote.reply.set(new Reply(mapping.status(), mapping.body()));
                assertConfirmationReason(() -> gateway.confirm("hold-id", "user-42", "Ana", "Garcia"),
                        mapping.reason());
            }
        }
    }

    @Test
    void lostConnectionDuringConfirmationHasUnknownOutcome() {
        var gateway = new CatedraHoldRestAdapter(RestClient.builder(), "http://127.0.0.1:1",
                "test-only-technical-jwt", Clock.systemUTC());

        assertConfirmationReason(() -> gateway.confirm("hold-id", "user-42", "Ana", "Garcia"),
                HoldConfirmationException.Reason.OUTCOME_UNKNOWN);
    }

    private static CatedraHoldRestAdapter adapter(Remote remote) {
        return new CatedraHoldRestAdapter(RestClient.builder(), remote.baseUrl(),
                "test-only-technical-jwt", Clock.systemUTC());
    }

    private static Remote remote(Reply initial) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var reply = new AtomicReference<>(initial);
        var method = new AtomicReference<String>();
        var path = new AtomicReference<String>();
        var authorization = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        server.createContext("/", request -> {
            method.set(request.getRequestMethod());
            path.set(request.getRequestURI().getPath());
            authorization.set(request.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            send(request, reply.get());
        });
        server.start();
        return new Remote(server, "http://127.0.0.1:" + server.getAddress().getPort(), reply, method, path,
                authorization, body);
    }

    private static void send(HttpExchange request, Reply reply) throws IOException {
        byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type", "application/json");
        request.sendResponseHeaders(reply.status(), body.length);
        try (var output = request.getResponseBody()) {
            output.write(body);
        }
    }

    private static void assertConfirmationReason(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
                                                HoldConfirmationException.Reason reason) {
        assertThatThrownBy(action).isInstanceOfSatisfying(HoldConfirmationException.class,
                exception -> assertThat(exception.reason()).isEqualTo(reason));
    }

    private record Reply(int status, String body) { }

    private record Mapping(int status, String body, HoldConfirmationException.Reason reason) { }

    private record Remote(HttpServer server, String baseUrl, AtomicReference<Reply> reply,
                          AtomicReference<String> method, AtomicReference<String> path,
                          AtomicReference<String> authorization, AtomicReference<String> body) implements AutoCloseable {
        @Override
        public void close() {
            server.stop(0);
        }
    }
}

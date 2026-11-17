package ar.edu.turnos.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationOperationException;
import ar.edu.turnos.turnos.reservation.infrastructure.rest.CatedraReservationsRestAdapter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class CatedraReservationsRestAdapterTests {
    private static final String PATIENT = "user-42";
    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>("[]");
    private final AtomicReference<String> count = new AtomicReference<>("1");
    private int status = 200;

    @AfterEach
    void stop() {
        if (server != null) { server.stop(0); }
    }

    @Test
    void aValidListingIsMappedAndOnlyTheRequestedPatientIsKept() throws Exception {
        body.set("""
                [{"reservationId":8251,"reservationProcessId":"process-1","externalPatientId":"user-42",
                  "appointmentDate":"2026-11-14","startTime":"09:30:00","endTime":"10:00:00",
                  "professionalId":15,"status":"CONFIRMED","confirmedAt":"2026-11-10T12:00:00Z"},
                 {"reservationId":8252,"reservationProcessId":"process-2","externalPatientId":"user-99",
                  "appointmentDate":"2026-11-14","startTime":"09:30:00","endTime":"10:00:00",
                  "professionalId":15,"status":"CONFIRMED","confirmedAt":"2026-11-10T12:00:00Z"}]
                """);
        count.set("2");

        var page = adapter().list(PATIENT, 0, 20);

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.returnedCount()).isEqualTo(2);
        assertThat(page.content()).extracting(item -> item.processId()).containsExactly("process-1");
    }

    @Test
    void aMissingTechnicalTokenIsAnIntegrationFailure() {
        var adapter = new CatedraReservationsRestAdapter(RestClient.builder(), "http://127.0.0.1:1", " ");

        assertThatThrownBy(() -> adapter.list(PATIENT, 0, 20))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.INTEGRATION_UNAVAILABLE));
        assertThatThrownBy(() -> adapter.cancel("process-1", null))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.INTEGRATION_UNAVAILABLE));
    }

    @Test
    void anUnauthorizedCourseResponseIsAnIntegrationFailure() throws Exception {
        status = 401;

        assertThatThrownBy(() -> adapter().list(PATIENT, 0, 20))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.INTEGRATION_UNAVAILABLE));
    }

    @Test
    void anInvalidListingIsReportedAsAnInvalidResponse() throws Exception {
        body.set("not-json");

        assertThatThrownBy(() -> adapter().list(PATIENT, 0, 20))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.INVALID_RESPONSE));
    }

    @Test
    void anOutOfRangeTotalIsReportedAsAnInvalidResponse() throws Exception {
        count.set("10001");

        assertThatThrownBy(() -> adapter().list(PATIENT, 0, 20))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.INVALID_RESPONSE));
    }

    @Test
    void aListingWithAnUnknownStatusIsReportedAsAnInvalidResponse() throws Exception {
        body.set("""
                [{"reservationId":8251,"reservationProcessId":"process-1","externalPatientId":"user-42",
                  "appointmentDate":"2026-11-14","startTime":"09:30:00","endTime":"10:00:00",
                  "professionalId":15,"status":"WHATEVER"}]
                """);

        assertThatThrownBy(() -> adapter().list(PATIENT, 0, 20))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.INVALID_RESPONSE));
    }

    @Test
    void aValidCancellationIsMapped() throws Exception {
        body.set("""
                {"reservationId":8251,"reservationProcessId":"process-1","status":"CANCELLED",
                 "cancelledAt":"2026-11-10T14:00:00Z"}
                """);

        var receipt = adapter().cancel("process-1", "Cambio de plan");

        assertThat(receipt.reservationId()).isEqualTo(8251);
        assertThat(receipt.cancelledAt()).isEqualTo(java.time.Instant.parse("2026-11-10T14:00:00Z"));
    }

    @Test
    void aCancellationOfAnotherProcessIsNotAccepted() throws Exception {
        body.set("""
                {"reservationId":8251,"reservationProcessId":"other-process","status":"CANCELLED",
                 "cancelledAt":"2026-11-10T14:00:00Z"}
                """);

        assertThatThrownBy(() -> adapter().cancel("process-1", null))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.OUTCOME_UNKNOWN));
    }

    @Test
    void aCancellationWithoutTimestampLeavesTheOutcomeUnknown() throws Exception {
        body.set("""
                {"reservationId":8251,"reservationProcessId":"process-1","status":"CANCELLED","cancelledAt":null}
                """);

        assertThatThrownBy(() -> adapter().cancel("process-1", null))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.OUTCOME_UNKNOWN));
    }

    @Test
    void aServerFailureDuringCancellationLeavesTheOutcomeUnknown() throws Exception {
        status = 503;

        assertThatThrownBy(() -> adapter().cancel("process-1", null))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.OUTCOME_UNKNOWN));
    }

    @Test
    void anUnreachableCourseIsAnIntegrationFailure() {
        var adapter = new CatedraReservationsRestAdapter(RestClient.builder(), "http://127.0.0.1:1",
                "test-technical-jwt");

        assertThatThrownBy(() -> adapter.list(PATIENT, 0, 20))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.INTEGRATION_UNAVAILABLE));
    }

    private CatedraReservationsRestAdapter adapter() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::answer);
        server.start();
        return new CatedraReservationsRestAdapter(RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-technical-jwt");
    }

    private void answer(HttpExchange exchange) throws IOException {
        byte[] payload = body.get().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("X-Total-Count", count.get());
        exchange.sendResponseHeaders(status, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }
}

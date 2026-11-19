package ar.edu.turnos.turnos.reservation.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.turnos.turnos.reservation.application.exception.HoldConfirmationException;
import ar.edu.turnos.turnos.reservation.application.service.ReservationService;
import ar.edu.turnos.turnos.reservation.infrastructure.web.mapper.ReservationDtoMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ReservationControllerTests {
    private final ReservationController controller = new ReservationController(
            (ReservationService) null, (ReservationDtoMapper) null);

    @Test
    void mapsEachConfirmationFailureToItsPublicHttpStatus() {
        Map<HoldConfirmationException.Reason, HttpStatus> expected = Map.ofEntries(
                Map.entry(HoldConfirmationException.Reason.PROCESS_NOT_FOUND, HttpStatus.NOT_FOUND),
                Map.entry(HoldConfirmationException.Reason.HOLD_NOT_FOUND, HttpStatus.NOT_FOUND),
                Map.entry(HoldConfirmationException.Reason.PROFESSIONAL_NOT_FOUND, HttpStatus.NOT_FOUND),
                Map.entry(HoldConfirmationException.Reason.PROCESS_NOT_OWNED, HttpStatus.FORBIDDEN),
                Map.entry(HoldConfirmationException.Reason.HOLD_EXPIRED, HttpStatus.GONE),
                Map.entry(HoldConfirmationException.Reason.INVALID_STATE, HttpStatus.CONFLICT),
                Map.entry(HoldConfirmationException.Reason.CONFIRMATION_IN_PROGRESS, HttpStatus.CONFLICT),
                Map.entry(HoldConfirmationException.Reason.SLOT_UNAVAILABLE, HttpStatus.CONFLICT),
                Map.entry(HoldConfirmationException.Reason.USER_PROFILE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE),
                Map.entry(HoldConfirmationException.Reason.INTEGRATION_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE),
                Map.entry(HoldConfirmationException.Reason.OUTCOME_UNKNOWN, HttpStatus.SERVICE_UNAVAILABLE),
                Map.entry(HoldConfirmationException.Reason.INVALID_RESPONSE, HttpStatus.BAD_GATEWAY),
                Map.entry(HoldConfirmationException.Reason.ALREADY_CONFIRMED, HttpStatus.ACCEPTED));

        expected.forEach((reason, status) -> {
            ProblemDetail result = controller.confirmationError(new HoldConfirmationException(reason, "local-id"));
            assertThat(result.getStatus()).isEqualTo(status.value());
            assertThat(result.getProperties()).containsEntry("code", reason.name())
                    .containsEntry("processId", "local-id");
        });
    }

    @Test
    void omitsProcessIdWhenTheFailureDoesNotHaveOne() {
        ProblemDetail result = controller.confirmationError(
                new HoldConfirmationException(HoldConfirmationException.Reason.PROCESS_NOT_FOUND));

        assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(result.getProperties()).containsEntry("code", "PROCESS_NOT_FOUND")
                .doesNotContainKey("processId");
    }
}

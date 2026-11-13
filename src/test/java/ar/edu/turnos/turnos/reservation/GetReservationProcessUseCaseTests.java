package ar.edu.turnos.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.turnos.turnos.reservation.application.usecases.GetReservationProcessUseCaseImpl;
import ar.edu.turnos.turnos.reservation.domain.model.PhoneExchangeException;
import ar.edu.turnos.turnos.reservation.domain.model.PhoneSubmission;
import ar.edu.turnos.turnos.reservation.domain.model.RecoveryProgress;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.ports.out.PhoneExchangeRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.RecoveryRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetReservationProcessUseCaseTests {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private final ReservationProcessRepository processes = mock(ReservationProcessRepository.class);
    private final PhoneExchangeRepository exchange = mock(PhoneExchangeRepository.class);
    private final RecoveryRepository recovery = mock(RecoveryRepository.class);
    private final GetReservationProcessUseCaseImpl useCase = new GetReservationProcessUseCaseImpl(processes, exchange,
            Clock.fixed(NOW, ZoneOffset.UTC), recovery);

    @Test
    void anInvalidIdentifierIsRejected() {
        assertThatThrownBy(() -> useCase.get(null, 42)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.get(" ", 42)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.get("local-1", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aProcessOfAnotherOwnerIsNotExposed() {
        when(processes.findById("local-1")).thenReturn(Optional.of(process(42)));

        assertThatThrownBy(() -> useCase.get("local-1", 7))
                .isInstanceOfSatisfying(PhoneExchangeException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(PhoneExchangeException.Reason.PROCESS_NOT_OWNED));
    }

    @Test
    void thePhoneCanOnlyBeSubmittedWithARequestAndBeforeItsDeadline() {
        when(processes.findById("local-1")).thenReturn(Optional.of(process(42)));
        when(exchange.latest("local-1")).thenReturn(Optional.of(
                new PhoneSubmission("event-1", "local-1", "integration", "process-1", "hold-1", "request-1",
                        "1234567", NOW, NOW.plusSeconds(600), PhoneSubmission.DeliveryStatus.PUBLISHED, 1)));

        assertThat(useCase.get("local-1", 42).canSubmitPhone()).isTrue();
    }

    @Test
    void theDeliveryStatusAndRecoveryDiagnosticAreExposedTogether() {
        when(processes.findById("local-1")).thenReturn(Optional.of(process(42)));
        when(exchange.latest("local-1")).thenReturn(Optional.of(
                new PhoneSubmission("event-1", "local-1", "integration", "process-1", "hold-1", "request-1",
                        "1234567", NOW, NOW.plusSeconds(600), PhoneSubmission.DeliveryStatus.PUBLISHED, 1)));
        when(recovery.get("local-1")).thenReturn(Optional.of(
                new RecoveryProgress("local-1", ReservationProcess.Status.WAITING_FOR_PHONE.name(),
                        RecoveryProgress.State.REVIEW, 5, NOW, NOW, "ATTEMPTS_EXHAUSTED")));

        var view = useCase.get("local-1", 42);

        assertThat(view.deliveryStatus()).isEqualTo(PhoneSubmission.DeliveryStatus.PUBLISHED);
        assertThat(view.recovery()).isNotNull();
        assertThat(view.recovery().state()).isEqualTo(RecoveryProgress.State.REVIEW);
        assertThat(view.recovery().lastErrorCode()).isEqualTo("ATTEMPTS_EXHAUSTED");
    }

    @Test
    void aProcessWithoutDeliveryOrRecoveryShowsNeither() {
        when(processes.findById("local-1")).thenReturn(Optional.of(process(42)));

        var view = useCase.get("local-1", 42);

        assertThat(view.deliveryStatus()).isNull();
        assertThat(view.recovery()).isNull();
    }

    private static ReservationProcess process(long owner) {
        return new ReservationProcess("local-1", owner, 15, LocalDate.of(2026, 12, 7), LocalTime.of(9, 30),
                ReservationProcess.Status.WAITING_FOR_PHONE, "hold-1", "process-1", NOW.plusSeconds(600),
                "request-1", "PHONE_NUMBER", null, NOW.minusSeconds(60), NOW, null);
    }
}

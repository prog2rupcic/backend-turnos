package ar.edu.turnos.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.turnos.turnos.reservation.application.usecases.CancellationProgressUseCaseImpl;
import ar.edu.turnos.turnos.reservation.domain.model.CancellationReceipt;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationOperationException;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.ports.out.PhoneExchangeRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CancellationProgressUseCaseTests {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private final ReservationProcessRepository processes = mock(ReservationProcessRepository.class);
    private final PhoneExchangeRepository locks = mock(PhoneExchangeRepository.class);
    private final CancellationProgressUseCaseImpl useCase = new CancellationProgressUseCaseImpl(processes, locks,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private void savingReturnsTheSavedProcess() {
        when(processes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void anUnknownProcessCannotBeCancelled() {
        when(processes.findById("local-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.begin("local-1", 42, null))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.PROCESS_NOT_FOUND));
    }

    @Test
    void anotherOwnerCannotCancel() {
        when(processes.findById("local-1")).thenReturn(Optional.of(confirmed(42)));

        assertThatThrownBy(() -> useCase.begin("local-1", 7, null))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.NOT_OWNER));
    }

    @Test
    void anAlreadyCancelledProcessIsReturnedWithoutAnotherEffect() {
        when(processes.findById("local-1")).thenReturn(Optional.of(cancelled()));

        assertThat(useCase.begin("local-1", 42, null).status()).isEqualTo(ReservationProcess.Status.CANCELLED);
        verify(processes, never()).save(any());
    }

    @Test
    void aCancellationInFlightCannotBeStartedAgain() {
        when(processes.findById("local-1")).thenReturn(Optional.of(
                confirmed(42).cancellationRequested("Cambio", NOW)));

        assertThatThrownBy(() -> useCase.begin("local-1", 42, "Otro"))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.CANCELLATION_IN_PROGRESS));
    }

    @Test
    void aProcessThatIsNotConfirmedCannotBeCancelled() {
        when(processes.findById("local-1")).thenReturn(Optional.of(new ReservationProcess("local-1", 42, 15,
                LocalDate.of(2026, 12, 7), LocalTime.of(9, 30), ReservationProcess.Status.WAITING_FOR_PHONE,
                "hold-1", "process-1", NOW.plusSeconds(600), "request-1", "PHONE_NUMBER", null, NOW, NOW, null)));

        assertThatThrownBy(() -> useCase.begin("local-1", 42, null))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.INVALID_STATE));
    }

    @Test
    void aConfirmedProcessStartsTheCancellationWithItsReason() {
        savingReturnsTheSavedProcess();
        when(processes.findById("local-1")).thenReturn(Optional.of(confirmed(42)));

        var result = useCase.begin("local-1", 42, "Cambio de plan");

        assertThat(result.status()).isEqualTo(ReservationProcess.Status.CANCEL_REQUESTING);
        assertThat(result.cancellationReason()).isEqualTo("Cambio de plan");
    }

    @Test
    void completingWithAnotherReservationIdLeavesTheOutcomeUnknown() {
        when(processes.findById("local-1")).thenReturn(Optional.of(
                confirmed(42).cancellationRequested("Cambio", NOW)));

        assertThatThrownBy(() -> useCase.complete("local-1",
                new CancellationReceipt(9999, "process-1", NOW), null))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.OUTCOME_UNKNOWN));
    }

    @Test
    void completingWithAnotherProcessIdLeavesTheOutcomeUnknown() {
        when(processes.findById("local-1")).thenReturn(Optional.of(
                confirmed(42).cancellationRequested("Cambio", NOW)));

        assertThatThrownBy(() -> useCase.complete("local-1",
                new CancellationReceipt(8251, "other-process", NOW), null))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.OUTCOME_UNKNOWN));
    }

    @Test
    void completingAProcessThatIsNotCancellingIsRejected() {
        when(processes.findById("local-1")).thenReturn(Optional.of(confirmed(42)));

        assertThatThrownBy(() -> useCase.complete("local-1",
                new CancellationReceipt(8251, "process-1", NOW), null))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.OUTCOME_UNKNOWN));
    }

    @Test
    void completingAnAlreadyCancelledProcessIsIdempotent() {
        when(processes.findById("local-1")).thenReturn(Optional.of(cancelled()));

        assertThat(useCase.complete("local-1", new CancellationReceipt(8251, "process-1", NOW), null).status())
                .isEqualTo(ReservationProcess.Status.CANCELLED);
        verify(processes, never()).save(any());
    }

    @Test
    void completingAnUnknownProcessIsRejected() {
        when(processes.findById("local-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.complete("local-1",
                new CancellationReceipt(8251, "process-1", NOW), null))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.PROCESS_NOT_FOUND));
    }

    @Test
    void anUncertainOutcomeOnlyAffectsAPendingCancellation() {
        when(processes.findById("local-1")).thenReturn(Optional.of(
                confirmed(42).cancellationRequested("Cambio", NOW)));

        useCase.uncertain("local-1");

        verify(processes).save(any(ReservationProcess.class));
    }

    @Test
    void anUncertainOutcomeDoesNotAffectAProcessThatIsNotCancelling() {
        when(processes.findById("local-1")).thenReturn(Optional.of(confirmed(42)));

        useCase.uncertain("local-1");

        verify(processes, never()).save(any());
    }

    @Test
    void aRejectedCancellationRestoresTheConfirmedState() {
        when(processes.findById("local-1")).thenReturn(Optional.of(
                confirmed(42).cancellationRequested("Cambio", NOW)));

        useCase.rejected("local-1");

        verify(processes).save(any(ReservationProcess.class));
    }

    @Test
    void aRejectedCancellationDoesNotAffectAProcessThatIsNotCancelling() {
        when(processes.findById("local-1")).thenReturn(Optional.of(confirmed(42)));

        useCase.rejected("local-1");

        verify(processes, never()).save(any());
    }

    private static ReservationProcess confirmed(long owner) {
        var now = Instant.parse("2026-11-10T11:00:00Z");
        return new ReservationProcess("local-1", owner, 15, LocalDate.of(2026, 12, 7), LocalTime.of(9, 30),
                ReservationProcess.Status.CONFIRMED, "hold-1", "process-1", NOW.plusSeconds(600), "request-1",
                "PHONE_NUMBER", null, now, now, null).confirmed(8251, now, now);
    }

    private static ReservationProcess cancelled() {
        var now = Instant.parse("2026-11-10T11:00:00Z");
        return confirmed(42).cancelled(8251L, now, "Cambio", now);
    }
}

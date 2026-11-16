package ar.edu.turnos.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.turnos.turnos.reservation.application.usecases.ApplyRecoveryUseCaseImpl;
import ar.edu.turnos.turnos.reservation.domain.model.CreatedHold;
import ar.edu.turnos.turnos.reservation.domain.model.OwnReservation;
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

class ApplyRecoveryUseCaseTests {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 12, 7);
    private static final LocalTime TIME = LocalTime.of(9, 30);
    private final ReservationProcessRepository processes = mock(ReservationProcessRepository.class);
    private final PhoneExchangeRepository exchange = mock(PhoneExchangeRepository.class);
    private final RecoveryRepository recovery = mock(RecoveryRepository.class);
    private final ApplyRecoveryUseCaseImpl useCase = new ApplyRecoveryUseCaseImpl(processes, exchange, recovery,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aLostLeaseDiscardsTheRemoteOutcome() {
        when(recovery.get("local-1")).thenReturn(Optional.empty());

        assertThat(useCase.remote(claim("local-1", ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN),
                remote("process-1", "CONFIRMED", 8251L, NOW))).isEqualTo("LEASE_LOST");
        verify(processes, never()).save(any());
        verify(recovery, never()).done(any());
    }

    @Test
    void aProcessThatChangedPhaseIsClosedWithoutApplyingTheRemoteOutcome() {
        leased();
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.EXPIRED, "process-1", 8251L)));

        assertThat(useCase.remote(claim("local-1", ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN),
                remote("process-1", "CONFIRMED", 8251L, NOW))).isEqualTo("STATE_CHANGED");
        verify(recovery).done(any());
    }

    @Test
    void aMissingLocalProcessIsClosed() {
        leased();
        when(processes.findById("local-1")).thenReturn(Optional.empty());

        assertThat(useCase.remote(claim("local-1", ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN),
                remote("process-1", "CONFIRMED", 8251L, NOW))).isEqualTo("STATE_CHANGED");
    }

    @Test
    void aRemoteReservationIdThatDiffersFromTheLocalOneNeedsReview() {
        leased();
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN, "process-1", 8251L)));

        assertThat(useCase.remote(claim("local-1", ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN),
                remote("process-1", "CONFIRMED", 9999L, NOW))).isEqualTo("REVIEW");
        verify(recovery).review(any(), org.mockito.ArgumentMatchers.eq("REMOTE_MISMATCH"));
    }

    @Test
    void aConfirmationWithoutTimestampNeedsReview() {
        leased();
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN, "process-1", 8251L)));

        assertThat(useCase.remote(claim("local-1", ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN),
                remote("process-1", "CONFIRMED", 8251L, null))).isEqualTo("REVIEW");
    }

    @Test
    void aCancellationWithoutTimestampNeedsReview() {
        leased(ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN);
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN, "process-1", 8251L)));

        assertThat(useCase.remote(claim("local-1", ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN),
                remote("process-1", "CANCELLED", 8251L, NOW))).isEqualTo("REVIEW");
    }

    @Test
    void anUnexpectedRemoteStatusNeedsReview() {
        leased();
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN, "process-1", 8251L)));

        assertThat(useCase.remote(claim("local-1", ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN),
                remote("process-1", "OTHER", 8251L, NOW))).isEqualTo("REVIEW");
    }

    @Test
    void aPhonePendingRemoteForACancellingProcessNeedsReview() {
        leased(ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN);
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN, "process-1", 8251L)));

        assertThat(useCase.remote(claim("local-1", ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN),
                remote("process-1", "PHONE_PENDING", 8251L, NOW))).isEqualTo("REVIEW");
        verify(recovery).review(any(), org.mockito.ArgumentMatchers.eq("CANCEL_UNVERIFIED"));
    }

    @Test
    void aPhonePendingRemoteForAHeldProcessWaitsForTheKafkaRequest() {
        leased(ReservationProcess.Status.HELD);
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.HELD, "process-1", null)));

        assertThat(useCase.remote(claim("local-1", ReservationProcess.Status.HELD),
                remote("process-1", "PHONE_PENDING", 8251L, NOW))).isEqualTo("WAITING");
        verify(recovery).done(any());
    }

    @Test
    void aPhonePendingRemoteForAnAlreadySubmittedProcessWaitsForTheResult() {
        leased(ReservationProcess.Status.PHONE_SUBMITTED);
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.PHONE_SUBMITTED, "process-1", 8251L)));

        assertThat(useCase.remote(claim("local-1", ReservationProcess.Status.PHONE_SUBMITTED),
                remote("process-1", "PHONE_PENDING", 8251L, NOW))).isEqualTo("WAITING");
        verify(recovery).reschedule(any(), any(), org.mockito.ArgumentMatchers.eq("AWAITING_PHONE_RESULT"));
    }

    @Test
    void aFailedRemoteClosesTheProcessAsFailed() {
        leased(ReservationProcess.Status.PHONE_SUBMITTED);
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.PHONE_SUBMITTED, "process-1", 8251L)));

        assertThat(useCase.remote(claim("local-1", ReservationProcess.Status.PHONE_SUBMITTED),
                remote("process-1", "FAILED", 8251L, NOW))).isEqualTo("FAILED");
        verify(exchange).abandonPending("local-1");
    }

    @Test
    void aHoldIsOnlyAppliedWhenNoIdentifiersExistYet() {
        leased(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN);
        when(processes.findById("local-1")).thenReturn(Optional.of(processWithoutHold()));

        assertThat(useCase.hold(claim("local-1", ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN),
                new CreatedHold("hold-new", "process-new", NOW.plusSeconds(600)))).isEqualTo("HELD");
        verify(processes).save(any(ReservationProcess.class));
    }

    @Test
    void aHoldIsNotAppliedWhenTheProcessAlreadyHasIdentifiers() {
        leased(ReservationProcess.Status.HOLD_REQUESTING);
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.HOLD_REQUESTING, "process-1", 8251L)));

        assertThat(useCase.hold(claim("local-1", ReservationProcess.Status.HOLD_REQUESTING),
                new CreatedHold("hold-new", "process-new", NOW.plusSeconds(600)))).isEqualTo("STATE_CHANGED");
        verify(processes, never()).save(any());
    }

    @Test
    void aHoldIsNotAppliedWhenThePhaseNoLongerMatches() {
        leased(ReservationProcess.Status.HOLD_REQUESTING);
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.CONFIRMED, null, null)));

        assertThat(useCase.hold(claim("local-1", ReservationProcess.Status.HOLD_REQUESTING),
                new CreatedHold("hold-new", "process-new", NOW.plusSeconds(600)))).isEqualTo("STATE_CHANGED");
    }

    @Test
    void aHoldIsNotPublishedWithoutTheLease() {
        when(recovery.get("local-1")).thenReturn(Optional.empty());

        assertThat(useCase.hold(claim("local-1", ReservationProcess.Status.HOLD_REQUESTING),
                new CreatedHold("hold-new", "process-new", NOW.plusSeconds(600)))).isEqualTo("LEASE_LOST");
        verify(processes, never()).save(any());
    }

    @Test
    void theLeaseIsLostWhenTheAttemptCounterMovesForward() {
        when(recovery.get("local-1")).thenReturn(Optional.of(
                new RecoveryProgress("local-1", ReservationProcess.Status.HOLD_REQUESTING.name(),
                        RecoveryProgress.State.LEASED, 3, NOW, NOW, null)));

        assertThat(useCase.hold(claim("local-1", ReservationProcess.Status.HOLD_REQUESTING),
                new CreatedHold("hold-new", "process-new", NOW.plusSeconds(600)))).isEqualTo("LEASE_LOST");
    }

    @Test
    void aRemoteProcessThatDoesNotMatchIsNeverApplied() {
        leased();
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN, "process-1", 8251L)));

        assertThat(useCase.remote(claim("local-1", ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN),
                remote("other-process", "CONFIRMED", 8251L, NOW))).isEqualTo("REVIEW");
    }

    @Test
    void aRemoteSlotThatDoesNotMatchIsNeverApplied() {
        leased();
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN, "process-1", 8251L)));
        var otherSlot = new OwnReservation("process-1", 8251, 15, DATE.plusDays(1), TIME, LocalTime.of(10, 0),
                "Ana", "Gomez", "CONFIRMED", NOW, null, null);

        assertThat(useCase.remote(claim("local-1", ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN),
                otherSlot)).isEqualTo("REVIEW");
    }

    @Test
    void aConfirmedRemoteForACancellingProcessNeedsReview() {
        leased(ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN);
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN, "process-1", 8251L)));

        assertThat(useCase.remote(claim("local-1", ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN),
                remote("process-1", "CONFIRMED", 8251L, NOW))).isEqualTo("REVIEW");
        verify(recovery).review(any(), org.mockito.ArgumentMatchers.eq("CANCEL_OUTCOME_UNKNOWN"));
    }

    @Test
    void aPhonePendingRemoteForAConfirmationInFlightWaitsForTheKafkaRequest() {
        leased(ReservationProcess.Status.CONFIRM_REQUESTING);
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.CONFIRM_REQUESTING, "process-1", 8251L)));

        assertThat(useCase.remote(claim("local-1", ReservationProcess.Status.CONFIRM_REQUESTING),
                remote("process-1", "PHONE_PENDING", 8251L, NOW))).isEqualTo("WAITING");
        verify(recovery).done(any());
    }

    private void leased() { leased(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN); }

    private void leased(ReservationProcess.Status phase) {
        when(recovery.get("local-1")).thenReturn(Optional.of(
                new RecoveryProgress("local-1", phase.name(), RecoveryProgress.State.LEASED, 1, NOW, NOW, null)));
    }

    private static RecoveryProgress claim(String processId, ReservationProcess.Status phase) {
        return new RecoveryProgress(processId, phase.name(), RecoveryProgress.State.LEASED, 1, NOW, NOW, null);
    }

    private static ReservationProcess process(ReservationProcess.Status status, String externalId, Long reservationId) {
        return new ReservationProcess("local-1", 42, 15, DATE, TIME, status, "hold-1", externalId,
                NOW.plusSeconds(600), "request-1", "PHONE_NUMBER", null, NOW, NOW, null, reservationId, null,
                null, null);
    }

    private static ReservationProcess processWithoutHold() {
        return new ReservationProcess("local-1", 42, 15, DATE, TIME, ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN,
                null, null, NOW.plusSeconds(600), null, null, null, NOW, NOW, null, null, null, null, null);
    }

    private static OwnReservation remote(String processId, String status, Long reservationId, Instant confirmedAt) {
        return new OwnReservation(processId, reservationId, 15, DATE, TIME, LocalTime.of(10, 0), "Ana", "Gomez",
                status, confirmedAt, null, null);
    }
}

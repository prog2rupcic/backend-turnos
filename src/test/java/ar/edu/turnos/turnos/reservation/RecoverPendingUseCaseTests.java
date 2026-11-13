package ar.edu.turnos.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.turnos.turnos.reservation.application.usecases.RecoverPendingUseCaseImpl;
import ar.edu.turnos.turnos.reservation.domain.model.CreatedHold;
import ar.edu.turnos.turnos.reservation.domain.model.OwnReservation;
import ar.edu.turnos.turnos.reservation.domain.model.RecoveryProgress;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.model.SlotOccupancy;
import ar.edu.turnos.turnos.reservation.domain.ports.in.ApplyRecoveryUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.out.CourseReservationsGateway;
import ar.edu.turnos.turnos.reservation.domain.ports.out.HoldGateway;
import ar.edu.turnos.turnos.reservation.domain.ports.out.RecoveryRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.SlotAvailability;
import ar.edu.turnos.turnos.reservation.domain.ports.out.SlotOccupancyLookup;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecoverPendingUseCaseTests {
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 12, 7);
    private static final LocalTime TIME = LocalTime.of(9, 30);
    private final RecoveryRepository recovery = mock(RecoveryRepository.class);
    private final ReservationProcessRepository processes = mock(ReservationProcessRepository.class);
    private final CourseReservationsGateway reservations = mock(CourseReservationsGateway.class);
    private final SlotOccupancyLookup occupancies = mock(SlotOccupancyLookup.class);
    private final SlotAvailability availability = mock(SlotAvailability.class);
    private final HoldGateway holds = mock(HoldGateway.class);
    private final ApplyRecoveryUseCase apply = mock(ApplyRecoveryUseCase.class);
    private final RecoverPendingUseCaseImpl useCase = new RecoverPendingUseCaseImpl(recovery, processes, reservations,
            occupancies, availability, holds, apply, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aClaimWhoseProcessDisappearedIsClosed() {
        when(recovery.claim(NOW)).thenReturn(Optional.of(claim(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN)), Optional.empty());
        when(processes.findById("local-1")).thenReturn(Optional.empty());

        useCase.recover();

        verify(recovery).done(any());
        verify(reservations, never()).list(anyString(), anyInt(), anyInt());
    }

    @Test
    void aClaimWhosePhaseMovedIsClosedWithoutRemoteCalls() {
        when(recovery.claim(NOW)).thenReturn(Optional.of(claim(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN)), Optional.empty());
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.CONFIRMED, "process-1")));

        useCase.recover();

        verify(recovery).done(any());
        verify(reservations, never()).list(anyString(), anyInt(), anyInt());
    }

    @Test
    void aProcessWithoutExternalIdOutsideHoldPhasesNeedsReview() {
        when(recovery.claim(NOW)).thenReturn(Optional.of(claim(ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN)), Optional.empty());
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN, null)));

        useCase.recover();

        verify(recovery).review(any(), eq("MISSING_EXTERNAL_ID"));
    }

    @Test
    void anInvalidRemoteListingNeedsReview() {
        when(recovery.claim(NOW)).thenReturn(Optional.of(claim(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN)), Optional.empty());
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN, "process-1")));
        when(reservations.list("user-42", 0, 100)).thenReturn(
                new CourseReservationsGateway.Page(List.of(), 5, 0));

        useCase.recover();

        verify(recovery).review(any(), eq("REMOTE_INVALID"));
    }

    @Test
    void aMatchingRemoteReservationIsApplied() {
        when(recovery.claim(NOW)).thenReturn(Optional.of(claim(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN)), Optional.empty());
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN, "process-1")));
        var remote = new OwnReservation("process-1", 8251, 15, DATE, TIME, LocalTime.of(10, 0), "Ana", "Gomez",
                "CONFIRMED", NOW, null, null);
        when(reservations.list("user-42", 0, 100)).thenReturn(
                new CourseReservationsGateway.Page(List.of(remote), 1, 1));

        useCase.recover();

        verify(apply).remote(any(), eq(remote));
    }

    @Test
    void aVisibleReservationOfAnotherProcessIsNotApplied() {
        when(recovery.claim(NOW)).thenReturn(Optional.of(claim(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN)), Optional.empty());
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN, "process-1")));
        var other = new OwnReservation("process-other", 8252, 15, DATE, TIME, LocalTime.of(10, 0), "Ana", "Gomez",
                "CONFIRMED", NOW, null, null);
        when(reservations.list("user-42", 0, 100)).thenReturn(
                new CourseReservationsGateway.Page(List.of(other), 1, 1));

        useCase.recover();

        verify(apply, never()).remote(any(), any());
        verify(recovery).reschedule(any(), any(), eq("NOT_YET_VISIBLE"));
    }

    @Test
    void anUnseenCancellationIsPostponedWithoutReposting() {
        when(recovery.claim(NOW)).thenReturn(Optional.of(claim(ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN)), Optional.empty());
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN, "process-1")));
        when(reservations.list("user-42", 0, 100)).thenReturn(
                new CourseReservationsGateway.Page(List.of(), 0, 0));

        useCase.recover();

        verify(recovery).reschedule(any(), any(), eq("CANCEL_UNVERIFIED"));
        verify(reservations, never()).cancel(anyString(), any());
    }

    @Test
    void aHeldSlotWithoutOwnershipWaitsForItsExpiry() {
        when(recovery.claim(NOW)).thenReturn(Optional.of(claim(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN)), Optional.empty());
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN, null)));
        when(occupancies.get(15, DATE, TIME)).thenReturn(
                new SlotOccupancy(SlotOccupancy.Status.HELD, NOW.plusSeconds(300)));

        useCase.recover();

        var captor = ArgumentCaptor.forClass(Instant.class);
        verify(recovery).reschedule(any(), captor.capture(), eq("HOLD_UNATTRIBUTABLE"));
        assertThat(captor.getValue()).isAfterOrEqualTo(NOW.plusSeconds(300));
    }

    @Test
    void aHeldSlotWithoutExpiryIsPostponedWithABoundedDelay() {
        when(recovery.claim(NOW)).thenReturn(Optional.of(claim(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN)), Optional.empty());
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN, null)));
        when(occupancies.get(15, DATE, TIME)).thenReturn(new SlotOccupancy(SlotOccupancy.Status.HELD, null));

        useCase.recover();

        verify(recovery).reschedule(any(), any(), eq("HOLD_UNATTRIBUTABLE"));
    }

    @Test
    void aFreeSlotWaitsForTheCourseDefaultTtlBeforeAnyNewAttempt() {
        when(recovery.claim(NOW)).thenReturn(Optional.of(claim(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN)), Optional.empty());
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN, null, NOW.minusSeconds(60))));
        when(occupancies.get(15, DATE, TIME)).thenReturn(new SlotOccupancy(SlotOccupancy.Status.FREE, null));

        useCase.recover();

        verify(recovery).reschedule(any(), any(), eq("WAITING_HOLD_TTL"));
        verify(holds, never()).create(anyLong(), any(), any(), anyString());
    }

    @Test
    void aFreeSlotObservedOnceIsNotEnoughToCreateANewHold() {
        when(recovery.claim(NOW)).thenReturn(Optional.of(claim(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN)),
                Optional.empty());
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN, null)));
        when(occupancies.get(15, DATE, TIME)).thenReturn(new SlotOccupancy(SlotOccupancy.Status.FREE, null));

        useCase.recover();

        verify(recovery).reschedule(any(), any(), eq("SLOT_FREE"));
        verify(holds, never()).create(anyLong(), any(), any(), anyString());
    }

    @Test
    void aSlotThatBecameUnavailablePostponesTheAttempt() {
        when(recovery.claim(NOW)).thenReturn(Optional.of(
                claim(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN, "SLOT_FREE", NOW.minusSeconds(60))),
                Optional.empty());
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN, null)));
        when(occupancies.get(15, DATE, TIME)).thenReturn(new SlotOccupancy(SlotOccupancy.Status.FREE, null));
        when(availability.isAvailable(15, DATE, TIME)).thenReturn(false);

        useCase.recover();

        verify(recovery).reschedule(any(), any(), eq("SLOT_UNAVAILABLE"));
        verify(holds, never()).create(anyLong(), any(), any(), anyString());
    }

    @Test
    void aRejectedNewHoldIsPostponedBecauseOwnershipStaysUnproven() {
        when(recovery.claim(NOW)).thenReturn(Optional.of(
                claim(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN, "SLOT_FREE", NOW.minusSeconds(60))),
                Optional.empty());
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN, null)));
        when(occupancies.get(15, DATE, TIME)).thenReturn(new SlotOccupancy(SlotOccupancy.Status.FREE, null));
        when(availability.isAvailable(15, DATE, TIME)).thenReturn(true);
        when(holds.create(15, DATE, TIME, "user-42")).thenThrow(
                new ar.edu.turnos.turnos.reservation.application.exception.HoldCreationException(
                        ar.edu.turnos.turnos.reservation.application.exception.HoldCreationException.Reason.SLOT_UNAVAILABLE,
                        "local-1"));

        useCase.recover();

        verify(recovery).reschedule(any(), any(), eq("SLOT_UNAVAILABLE"));
        verify(apply, never()).hold(any(), any());
    }

    @Test
    void aFreeSlotAfterTheTtlAndTwoObservationsCreatesANewHold() {
        when(recovery.claim(NOW)).thenReturn(Optional.of(
                claim(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN, "SLOT_FREE", NOW.minusSeconds(60))),
                Optional.empty());
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN, null)));
        when(occupancies.get(15, DATE, TIME)).thenReturn(new SlotOccupancy(SlotOccupancy.Status.FREE, null));
        when(availability.isAvailable(15, DATE, TIME)).thenReturn(true);
        var hold = new CreatedHold("hold-new", "process-new", NOW.plusSeconds(600));
        when(holds.create(15, DATE, TIME, "user-42")).thenReturn(hold);

        useCase.recover();

        verify(apply).hold(any(), eq(hold));
    }

    @Test
    void anUnexpectedFailureReschedulesWithoutChangingTheProcess() {
        when(recovery.claim(NOW)).thenReturn(Optional.of(claim(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN)), Optional.empty());
        when(processes.findById("local-1")).thenReturn(Optional.of(
                process(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN, "process-1")));
        when(reservations.list(anyString(), anyInt(), anyInt())).thenThrow(new IllegalStateException("sin red"));

        useCase.recover();

        verify(recovery).reschedule(any(), any(), eq("SOURCE_UNAVAILABLE"));
    }

    @Test
    void aCycleWithoutClaimsStopsImmediately() {
        when(recovery.claim(NOW)).thenReturn(Optional.empty());

        useCase.recover();

        verify(recovery).discover(NOW);
        verify(reservations, never()).list(anyString(), anyInt(), anyInt());
    }

    private static RecoveryProgress claim(ReservationProcess.Status phase) {
        return claim(phase, null, NOW);
    }

    private static RecoveryProgress claim(ReservationProcess.Status phase, String lastErrorCode,
                                         Instant lastCheckedAt) {
        return new RecoveryProgress("local-1", phase.name(), RecoveryProgress.State.LEASED, 2, NOW, lastCheckedAt,
                lastErrorCode);
    }

    private static ReservationProcess process(ReservationProcess.Status status, String externalId) {
        return process(status, externalId, NOW.minusSeconds(900));
    }

    private static ReservationProcess process(ReservationProcess.Status status, String externalId,
                                              Instant createdAt) {
        return new ReservationProcess("local-1", 42, 15, DATE, TIME, status, "hold-1", externalId,
                NOW.plusSeconds(600), "request-1", "PHONE_NUMBER", null, createdAt, NOW, null,
                null, null, null, null);
    }
}

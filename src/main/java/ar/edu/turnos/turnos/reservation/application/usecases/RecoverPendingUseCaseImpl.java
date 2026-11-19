package ar.edu.turnos.turnos.reservation.application.usecases;

import ar.edu.turnos.turnos.reservation.application.exception.HoldCreationException;
import ar.edu.turnos.turnos.reservation.domain.model.OwnReservation;
import ar.edu.turnos.turnos.reservation.domain.model.RecoveryProgress;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.model.SlotOccupancy;
import ar.edu.turnos.turnos.reservation.domain.ports.in.ApplyRecoveryUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.in.RecoverPendingUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.out.CourseReservationsGateway;
import ar.edu.turnos.turnos.reservation.domain.ports.out.HoldGateway;
import ar.edu.turnos.turnos.reservation.domain.ports.out.RecoveryRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.SlotAvailability;
import ar.edu.turnos.turnos.reservation.domain.ports.out.SlotOccupancyLookup;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class RecoverPendingUseCaseImpl implements RecoverPendingUseCase {
    private static final int REMOTE_PAGE_SIZE = 100;
    private final RecoveryRepository recovery;
    private final ReservationProcessRepository processes;
    private final CourseReservationsGateway reservations;
    private final SlotOccupancyLookup occupancies;
    private final SlotAvailability availability;
    private final HoldGateway holds;
    private final ApplyRecoveryUseCase apply;
    private final Clock clock;

    public RecoverPendingUseCaseImpl(RecoveryRepository recovery, ReservationProcessRepository processes,
                                     CourseReservationsGateway reservations, SlotOccupancyLookup occupancies,
                                     SlotAvailability availability, HoldGateway holds, ApplyRecoveryUseCase apply,
                                     Clock clock) {
        this.recovery = recovery;
        this.processes = processes;
        this.reservations = reservations;
        this.occupancies = occupancies;
        this.availability = availability;
        this.holds = holds;
        this.apply = apply;
        this.clock = clock;
    }

    @Override
    public void recover() {
        recovery.discover(clock.instant());
        for (int i = 0; i < 3; i++) {
            var claimed = recovery.claim(clock.instant());
            if (claimed.isEmpty()) { return; }
            var claim = claimed.get();
            try {
                handle(claim);
            } catch (RuntimeException _) {
                recovery.reschedule(claim, clock.instant().plusSeconds(backoff(claim.attempts())),
                        "SOURCE_UNAVAILABLE");
            }
        }
    }

    private void handle(RecoveryProgress claim) {
        var found = processes.findById(claim.processId());
        if (found.isEmpty() || !claim.phase().equals(found.get().status().name())) {
            recovery.done(claim);
            return;
        }
        var process = found.get();
        if (process.reservationProcessId() != null) {
            knownProcess(claim, process);
        } else if (process.status() == ReservationProcess.Status.HOLD_REQUESTING
                || process.status() == ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN) {
            unknownHold(claim, process);
        } else {
            recovery.review(claim, "MISSING_EXTERNAL_ID");
        }
    }

    private void knownProcess(RecoveryProgress claim, ReservationProcess process) {
        String patient = "user-" + process.ownerUserId();
        int page = 0;
        long total;
        do {
            var response = reservations.list(patient, page, REMOTE_PAGE_SIZE);
            total = response.totalElements();
            long offset = (long) page * REMOTE_PAGE_SIZE;
            if (total < 0 || total > 10_000 || response.returnedCount() != Math.min(REMOTE_PAGE_SIZE,
                    Math.max(0, total - offset))) {
                recovery.review(claim, "REMOTE_INVALID");
                return;
            }
            for (OwnReservation item : response.content()) {
                if (process.reservationProcessId().equals(item.processId())) {
                    apply.remote(claim, item);
                    return;
                }
            }
            page++;
        } while ((long) page * REMOTE_PAGE_SIZE < total);
        if (process.status() == ReservationProcess.Status.CANCEL_REQUESTING
                || process.status() == ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN) {
            recovery.reschedule(claim, clock.instant().plusSeconds(backoff(claim.attempts())), "CANCEL_UNVERIFIED");
        } else {
            recovery.reschedule(claim, clock.instant().plusSeconds(backoff(claim.attempts())), "NOT_YET_VISIBLE");
        }
    }

    private void unknownHold(RecoveryProgress claim, ReservationProcess process) {
        SlotOccupancy occupancy = occupancies.get(process.professionalId(), process.date(), process.startTime());
        if (occupancy.status() == SlotOccupancy.Status.CONFIRMED) {
            recovery.review(claim, "OCCUPIED_UNATTRIBUTABLE");
            return;
        }
        if (occupancy.status() == SlotOccupancy.Status.HELD) {
            Instant next = occupancy.expiresAt() == null ? clock.instant().plusSeconds(30)
                    : occupancy.expiresAt().plusSeconds(2);
            recovery.reschedule(claim, next.isAfter(clock.instant()) ? next : clock.instant().plusSeconds(30),
                    "HOLD_UNATTRIBUTABLE");
            return;
        }
        // No idempotency key exists for hold creation. Wait beyond the course's default ten-minute TTL,
        // then require two separate free observations before attempting a new hold.
        Instant safeAt = process.createdAt().plusSeconds(660);
        if (clock.instant().isBefore(safeAt)) {
            recovery.reschedule(claim, safeAt, "WAITING_HOLD_TTL");
            return;
        }
        if (!"SLOT_FREE".equals(claim.lastErrorCode()) || claim.lastCheckedAt() == null
                || clock.instant().isBefore(claim.lastCheckedAt().plusSeconds(10))) {
            recovery.reschedule(claim, clock.instant().plusSeconds(15), "SLOT_FREE");
            return;
        }
        if (!availability.isAvailable(process.professionalId(), process.date(), process.startTime())) {
            recovery.reschedule(claim, clock.instant().plusSeconds(backoff(claim.attempts())), "SLOT_UNAVAILABLE");
            return;
        }
        try {
            var hold = holds.create(process.professionalId(), process.date(), process.startTime(),
                    "user-" + process.ownerUserId());
            apply.hold(claim, hold);
        } catch (HoldCreationException exception) {
            // 409 is also inconclusive for the original hold: do not declare it ours or free.
            recovery.reschedule(claim, clock.instant().plusSeconds(backoff(claim.attempts())),
                    exception.reason().name());
        }
    }

    private static long backoff(int attempts) { return Math.min(300, 15L << Math.min(4, attempts - 1)); }
}

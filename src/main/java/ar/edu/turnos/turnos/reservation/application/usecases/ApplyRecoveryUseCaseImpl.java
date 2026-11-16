package ar.edu.turnos.turnos.reservation.application.usecases;

import ar.edu.turnos.turnos.reservation.domain.model.CreatedHold;
import ar.edu.turnos.turnos.reservation.domain.model.OwnReservation;
import ar.edu.turnos.turnos.reservation.domain.model.RecoveryProgress;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.model.PhoneSubmission;
import ar.edu.turnos.turnos.reservation.domain.ports.in.ApplyRecoveryUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.out.PhoneExchangeRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.RecoveryRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ApplyRecoveryUseCaseImpl implements ApplyRecoveryUseCase {
    private final ReservationProcessRepository processes;
    private final PhoneExchangeRepository exchange;
    private final RecoveryRepository recovery;
    private final Clock clock;

    public ApplyRecoveryUseCaseImpl(ReservationProcessRepository processes, PhoneExchangeRepository exchange,
                                    RecoveryRepository recovery, Clock clock) {
        this.processes = processes;
        this.exchange = exchange;
        this.recovery = recovery;
        this.clock = clock;
    }

    @Override
    @Transactional
    public String remote(RecoveryProgress claim, OwnReservation remote) {
        exchange.lockProcess(claim.processId());
        if (!ownsLease(claim)) { return "LEASE_LOST"; }
        var process = processes.findById(claim.processId()).orElse(null);
        if (process == null || !claim.phase().equals(process.status().name())) {
            recovery.done(claim);
            return "STATE_CHANGED";
        }
        if (!process.reservationProcessId().equals(remote.processId())
                || process.professionalId() != remote.professionalId() || !process.date().equals(remote.date())
                || !process.startTime().equals(remote.startTime())
                || process.reservationId() != null && process.reservationId() != remote.reservationId()) {
            recovery.review(claim, "REMOTE_MISMATCH");
            return "REVIEW";
        }
        var now = clock.instant();
        return switch (remote.status()) {
            case "CONFIRMED" -> {
                if (process.status() == ReservationProcess.Status.CANCEL_REQUESTING
                        || process.status() == ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN) {
                    recovery.review(claim, "CANCEL_OUTCOME_UNKNOWN");
                    yield "REVIEW";
                }
                if (remote.confirmedAt() == null) { recovery.review(claim, "REMOTE_MISMATCH"); yield "REVIEW"; }
                processes.save(process.confirmed(remote.reservationId(), remote.confirmedAt(), now));
                exchange.resolve(process.id(), PhoneSubmission.DeliveryStatus.COMPLETED);
                recovery.done(claim);
                yield "CONFIRMED";
            }
            case "CANCELLED" -> {
                if (remote.cancelledAt() == null) { recovery.review(claim, "REMOTE_MISMATCH"); yield "REVIEW"; }
                processes.save(process.cancelled(remote.reservationId(), remote.cancelledAt(),
                        remote.cancellationReason(), now));
                exchange.abandonPending(process.id());
                recovery.done(claim);
                yield "CANCELLED";
            }
            case "FAILED" -> {
                processes.save(process.withStatus(ReservationProcess.Status.FAILED, now));
                exchange.abandonPending(process.id());
                recovery.done(claim);
                yield "FAILED";
            }
            case "PHONE_PENDING" -> {
                if (process.status() == ReservationProcess.Status.CANCEL_REQUESTING
                        || process.status() == ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN) {
                    recovery.review(claim, "CANCEL_UNVERIFIED");
                    yield "REVIEW";
                }
                if (process.status() == ReservationProcess.Status.HELD
                        || process.status() == ReservationProcess.Status.CONFIRM_REQUESTING
                        || process.status() == ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN) {
                    processes.save(process.waitingForPhone(process.expiresAt(), now));
                    recovery.done(claim);
                    yield "WAITING";
                }
                // The course list cannot supply requestEventId, which must come from Kafka.
                recovery.reschedule(claim, now.plusSeconds(30), "AWAITING_PHONE_RESULT");
                yield "WAITING";
            }
            default -> { recovery.review(claim, "REMOTE_MISMATCH"); yield "REVIEW"; }
        };
    }

    @Override
    @Transactional
    public String hold(RecoveryProgress claim, CreatedHold hold) {
        exchange.lockProcess(claim.processId());
        if (!ownsLease(claim)) { return "LEASE_LOST"; }
        var process = processes.findById(claim.processId()).orElseThrow();
        if (process.reservationProcessId() != null || process.holdId() != null) {
            recovery.done(claim);
            return "STATE_CHANGED";
        }
        if (process.status() != ReservationProcess.Status.HOLD_REQUESTING
                && process.status() != ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN) {
            recovery.done(claim);
            return "STATE_CHANGED";
        }
        processes.save(process.withHold(hold.holdId(), hold.reservationProcessId(), hold.expiresAt(), clock.instant()));
        recovery.done(claim);
        return "HELD";
    }

    private boolean ownsLease(RecoveryProgress claim) {
        return recovery.get(claim.processId()).filter(current -> current.state() == RecoveryProgress.State.LEASED
                && current.attempts() == claim.attempts() && current.phase().equals(claim.phase())).isPresent();
    }

}

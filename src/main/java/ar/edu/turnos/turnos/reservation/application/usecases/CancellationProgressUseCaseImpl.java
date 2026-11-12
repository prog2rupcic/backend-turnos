package ar.edu.turnos.turnos.reservation.application.usecases;

import ar.edu.turnos.turnos.reservation.domain.model.CancellationReceipt;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationOperationException;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.ports.in.CancellationProgressUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.out.PhoneExchangeRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CancellationProgressUseCaseImpl implements CancellationProgressUseCase {
    private final ReservationProcessRepository processes;
    private final PhoneExchangeRepository locks;
    private final Clock clock;

    public CancellationProgressUseCaseImpl(ReservationProcessRepository processes, PhoneExchangeRepository locks,
                                           Clock clock) {
        this.processes = processes;
        this.locks = locks;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ReservationProcess begin(String id, long userId, String reason) {
        locks.lockProcess(id);
        var process = processes.findById(id).orElseThrow(() -> error(ReservationOperationException.Reason.PROCESS_NOT_FOUND));
        if (process.ownerUserId() != userId) { throw error(ReservationOperationException.Reason.NOT_OWNER); }
        if (process.status() == ReservationProcess.Status.CANCELLED) { return process; }
        if (process.status() == ReservationProcess.Status.CANCEL_REQUESTING
                || process.status() == ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN) {
            throw error(ReservationOperationException.Reason.CANCELLATION_IN_PROGRESS);
        }
        if (process.status() != ReservationProcess.Status.CONFIRMED || process.reservationId() == null
                || process.reservationProcessId() == null) {
            throw error(ReservationOperationException.Reason.INVALID_STATE);
        }
        return processes.save(process.cancellationRequested(reason, clock.instant()));
    }

    @Override
    @Transactional
    public ReservationProcess complete(String id, CancellationReceipt receipt, String reason) {
        locks.lockProcess(id);
        var process = processes.findById(id).orElseThrow(() -> error(ReservationOperationException.Reason.PROCESS_NOT_FOUND));
        if (process.status() == ReservationProcess.Status.CANCELLED) { return process; }
        if (process.status() != ReservationProcess.Status.CANCEL_REQUESTING
                && process.status() != ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN) {
            throw error(ReservationOperationException.Reason.OUTCOME_UNKNOWN);
        }
        if (process.reservationId() == null || process.reservationId() != receipt.reservationId()
                || !process.reservationProcessId().equals(receipt.reservationProcessId())) {
            throw error(ReservationOperationException.Reason.OUTCOME_UNKNOWN);
        }
        return processes.save(process.cancelled(receipt.cancelledAt(), reason, clock.instant()));
    }

    @Override
    @Transactional
    public void uncertain(String id) {
        locks.lockProcess(id);
        processes.findById(id).filter(process -> process.status() == ReservationProcess.Status.CANCEL_REQUESTING)
                .ifPresent(process -> processes.save(process.withStatus(
                        ReservationProcess.Status.CANCEL_OUTCOME_UNKNOWN, clock.instant())));
    }

    @Override
    @Transactional
    public void rejected(String id) {
        locks.lockProcess(id);
        processes.findById(id).filter(process -> process.status() == ReservationProcess.Status.CANCEL_REQUESTING)
                .ifPresent(process -> processes.save(process.cancellationRejected(clock.instant())));
    }

    private static ReservationOperationException error(ReservationOperationException.Reason reason) {
        return new ReservationOperationException(reason);
    }
}

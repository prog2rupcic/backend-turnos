package ar.edu.turnos.turnos.reservation.application.usecases;

import ar.edu.turnos.turnos.reservation.domain.model.CancellationNotice;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.ports.in.RecordCancellationUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.out.PhoneExchangeRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RecordCancellationUseCaseImpl implements RecordCancellationUseCase {
    private final ReservationProcessRepository processes;
    private final PhoneExchangeRepository exchange;
    private final Clock clock;

    public RecordCancellationUseCaseImpl(ReservationProcessRepository processes, PhoneExchangeRepository exchange,
                                         Clock clock) {
        this.processes = processes;
        this.exchange = exchange;
        this.clock = clock;
    }

    @Override
    @Transactional
    public String record(CancellationNotice notice) {
        exchange.lockProcess(notice.reservationProcessId());
        var found = processes.findByReservationProcessId(notice.reservationProcessId());
        if (found.isEmpty()) { return "UNKNOWN_PROCESS"; }
        var process = found.get();
        if (!Objects.equals(process.holdId(), notice.holdId())
                || !Objects.equals(process.requestEventId(), notice.requestEventId())
                || process.reservationId() != null && process.reservationId() != notice.reservationId()
                || process.professionalId() != notice.professionalId()
                || !process.date().equals(notice.date()) || !process.startTime().equals(notice.startTime())) {
            return "MISMATCH";
        }
        if (!exchange.markEvent(notice.eventId(), process.id(), clock.instant())) { return "DUPLICATE"; }
        if (process.status() == ReservationProcess.Status.CANCELLED
                || process.status() == ReservationProcess.Status.EXPIRED
                || process.status() == ReservationProcess.Status.INVALID
                || process.status() == ReservationProcess.Status.FAILED) { return "IGNORED"; }
        // A missed confirmation notification does not make the authoritative cancellation invalid.
        processes.save(process.cancelled(notice.reservationId(), notice.occurredAt(), notice.reason(), clock.instant()));
        exchange.abandonPending(process.id());
        return "APPLIED";
    }
}

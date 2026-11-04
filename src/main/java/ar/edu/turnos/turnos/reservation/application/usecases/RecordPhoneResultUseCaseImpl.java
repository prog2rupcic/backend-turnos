package ar.edu.turnos.turnos.reservation.application.usecases;

import ar.edu.turnos.turnos.reservation.domain.model.PhoneResult;
import ar.edu.turnos.turnos.reservation.domain.model.PhoneSubmission;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.ports.in.RecordPhoneResultUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.out.PhoneExchangeRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.Clock;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RecordPhoneResultUseCaseImpl implements RecordPhoneResultUseCase {
    private final ReservationProcessRepository processes;
    private final PhoneExchangeRepository exchange;
    private final Clock clock;

    public RecordPhoneResultUseCaseImpl(ReservationProcessRepository processes, PhoneExchangeRepository exchange,
                                       Clock clock) {
        this.processes = processes;
        this.exchange = exchange;
        this.clock = clock;
    }

    @Override
    @Transactional
    public String record(PhoneResult result) {
        exchange.lockProcess(result.reservationProcessId());
        var found = processes.findByReservationProcessId(result.reservationProcessId());
        if (found.isEmpty()) { return "UNKNOWN_PROCESS"; }
        var process = found.get();
        if (!Objects.equals(process.holdId(), result.holdId())
                || !Objects.equals(process.requestEventId(), result.requestEventId())) { return "MISMATCH"; }
        if (!exchange.markEvent(result.eventId(), process.id(), clock.instant())) { return "DUPLICATE"; }
        if (process.status() == ReservationProcess.Status.CONFIRMED
                || process.status() == ReservationProcess.Status.EXPIRED
                || process.status() == ReservationProcess.Status.FAILED) { return "IGNORED"; }
        if (result.confirmed()) {
            if (!Objects.equals(result.professionalId(), process.professionalId())
                    || !Objects.equals(result.date(), process.date())
                    || !Objects.equals(result.startTime(), process.startTime())) { return "MISMATCH"; }
            processes.save(process.confirmed(result.reservationId(), result.occurredAt(), clock.instant()));
            exchange.resolve(process.id(), PhoneSubmission.DeliveryStatus.COMPLETED);
        } else {
            var latest = exchange.latest(process.id());
            if (latest.isEmpty() || result.occurredAt().isBefore(latest.get().occurredAt())
                    || latest.get().deliveryStatus() == PhoneSubmission.DeliveryStatus.REJECTED) { return "IGNORED"; }
            processes.save(process.rejected(result.reason(), result.message(), result.expiresAt(), clock.instant()));
            exchange.resolve(process.id(), PhoneSubmission.DeliveryStatus.REJECTED);
        }
        return "APPLIED";
    }
}

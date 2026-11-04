package ar.edu.turnos.turnos.reservation.application.usecases;

import ar.edu.turnos.turnos.reservation.domain.model.PhoneExchangeException;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcessView;
import ar.edu.turnos.turnos.reservation.domain.ports.in.GetReservationProcessUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.out.PhoneExchangeRepository;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetReservationProcessUseCaseImpl implements GetReservationProcessUseCase {
    private final ReservationProcessRepository processes;
    private final PhoneExchangeRepository exchange;
    private final Clock clock;

    public GetReservationProcessUseCaseImpl(ReservationProcessRepository processes,
                                           PhoneExchangeRepository exchange, Clock clock) {
        this.processes = processes;
        this.exchange = exchange;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationProcessView get(String processId, long ownerUserId) {
        if (processId == null || processId.isBlank() || ownerUserId <= 0) {
            throw new IllegalArgumentException("Identificador inválido");
        }
        var process = processes.findById(processId).orElseThrow(() ->
                new PhoneExchangeException(PhoneExchangeException.Reason.PROCESS_NOT_FOUND));
        if (process.ownerUserId() != ownerUserId) {
            throw new PhoneExchangeException(PhoneExchangeException.Reason.PROCESS_NOT_OWNED);
        }
        boolean canSubmit = process.status() == ReservationProcess.Status.WAITING_FOR_PHONE
                && process.requestEventId() != null && "PHONE_NUMBER".equals(process.requestRequiredField())
                && process.expiresAt() != null && clock.instant().isBefore(process.expiresAt());
        return new ReservationProcessView(process, canSubmit,
                exchange.latest(processId).map(s -> s.deliveryStatus()).orElse(null));
    }
}

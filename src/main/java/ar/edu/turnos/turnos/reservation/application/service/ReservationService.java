package ar.edu.turnos.turnos.reservation.application.service;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.ports.in.CreateHoldUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.in.ConfirmHoldUseCase;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.stereotype.Service;

@Service
public class ReservationService {
    private final CreateHoldUseCase createHold;
    private final ConfirmHoldUseCase confirmHold;

    public ReservationService(CreateHoldUseCase createHold, ConfirmHoldUseCase confirmHold) {
        this.createHold = createHold;
        this.confirmHold = confirmHold;
    }

    public ReservationProcess createHold(long ownerUserId, long professionalId, LocalDate date, LocalTime startTime) {
        return createHold.create(ownerUserId, professionalId, date, startTime);
    }

    public ReservationProcess confirmHold(String processId, long ownerUserId) {
        return confirmHold.confirm(processId, ownerUserId);
    }
}

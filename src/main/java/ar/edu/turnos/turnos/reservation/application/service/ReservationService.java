package ar.edu.turnos.turnos.reservation.application.service;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.ports.in.CreateHoldUseCase;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.stereotype.Service;

@Service
public class ReservationService {
    private final CreateHoldUseCase createHold;

    public ReservationService(CreateHoldUseCase createHold) {
        this.createHold = createHold;
    }

    public ReservationProcess createHold(long ownerUserId, long professionalId, LocalDate date, LocalTime startTime) {
        return createHold.create(ownerUserId, professionalId, date, startTime);
    }
}

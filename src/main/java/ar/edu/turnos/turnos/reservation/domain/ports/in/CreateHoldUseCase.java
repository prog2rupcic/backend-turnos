package ar.edu.turnos.turnos.reservation.domain.ports.in;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import java.time.LocalDate;
import java.time.LocalTime;

public interface CreateHoldUseCase {
    ReservationProcess create(long ownerUserId, long professionalId, LocalDate date, LocalTime startTime);
}

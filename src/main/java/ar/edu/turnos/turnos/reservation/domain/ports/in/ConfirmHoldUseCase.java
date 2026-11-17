package ar.edu.turnos.turnos.reservation.domain.ports.in;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;

public interface ConfirmHoldUseCase {
    ReservationProcess confirm(String processId, long ownerUserId);
}

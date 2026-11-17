package ar.edu.turnos.turnos.reservation.domain.ports.in;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;

public interface CancelOwnReservationUseCase {
    ReservationProcess cancel(String localProcessId, long ownerUserId, String reason);
}

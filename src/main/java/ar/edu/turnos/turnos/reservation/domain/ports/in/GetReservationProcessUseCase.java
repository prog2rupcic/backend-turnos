package ar.edu.turnos.turnos.reservation.domain.ports.in;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcessView;

public interface GetReservationProcessUseCase {
    ReservationProcessView get(String processId, long ownerUserId);
}

package ar.edu.turnos.turnos.reservation.domain.ports.in;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationPage;

public interface ListOwnReservationsUseCase {
    ReservationPage list(long ownerUserId, int page, int size);
}

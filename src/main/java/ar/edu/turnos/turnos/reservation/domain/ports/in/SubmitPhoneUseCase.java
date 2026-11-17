package ar.edu.turnos.turnos.reservation.domain.ports.in;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcessView;

public interface SubmitPhoneUseCase {
    ReservationProcessView submit(String processId, long ownerUserId, String phoneNumber);
}

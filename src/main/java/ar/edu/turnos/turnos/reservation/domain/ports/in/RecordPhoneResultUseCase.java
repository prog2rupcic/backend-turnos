package ar.edu.turnos.turnos.reservation.domain.ports.in;

import ar.edu.turnos.turnos.reservation.domain.model.PhoneResult;

public interface RecordPhoneResultUseCase {
    String record(PhoneResult result);
}

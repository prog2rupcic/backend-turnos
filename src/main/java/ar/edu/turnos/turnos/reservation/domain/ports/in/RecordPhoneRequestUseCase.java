package ar.edu.turnos.turnos.reservation.domain.ports.in;

import ar.edu.turnos.turnos.reservation.domain.model.AdditionalInformationRequest;
import ar.edu.turnos.turnos.reservation.domain.model.PhoneRequestOutcome;

public interface RecordPhoneRequestUseCase {
    PhoneRequestOutcome record(AdditionalInformationRequest request);
}

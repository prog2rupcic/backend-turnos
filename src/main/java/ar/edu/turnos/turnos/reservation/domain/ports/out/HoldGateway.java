package ar.edu.turnos.turnos.reservation.domain.ports.out;

import ar.edu.turnos.turnos.reservation.domain.model.CreatedHold;
import ar.edu.turnos.turnos.reservation.domain.model.ConfirmedHold;
import java.time.LocalDate;
import java.time.LocalTime;

public interface HoldGateway {
    CreatedHold create(long professionalId, LocalDate date, LocalTime startTime, String externalPatientId);

    ConfirmedHold confirm(String holdId, String externalPatientId, String patientFirstName, String patientLastName);
}

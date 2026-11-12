package ar.edu.turnos.turnos.availability.domain.ports.in;

import ar.edu.turnos.turnos.availability.domain.model.Availability;
import java.time.LocalDate;

public interface GetAvailabilityUseCase {
    Availability get(long professionalId, LocalDate date);
}

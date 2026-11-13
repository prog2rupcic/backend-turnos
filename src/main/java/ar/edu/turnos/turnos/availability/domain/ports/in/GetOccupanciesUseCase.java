package ar.edu.turnos.turnos.availability.domain.ports.in;

import ar.edu.turnos.turnos.availability.domain.model.Occupancy;
import java.time.LocalDate;
import java.util.List;

public interface GetOccupanciesUseCase {
    List<Occupancy> get(long professionalId, LocalDate date);
}

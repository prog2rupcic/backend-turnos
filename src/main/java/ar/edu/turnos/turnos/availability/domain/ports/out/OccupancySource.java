package ar.edu.turnos.turnos.availability.domain.ports.out;

import ar.edu.turnos.turnos.availability.domain.model.Occupancy;
import java.time.LocalDate;
import java.util.List;

public interface OccupancySource {
    List<Occupancy> find(long professionalId, LocalDate date);
}

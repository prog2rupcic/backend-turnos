package ar.edu.turnos.turnos.availability.application.usecases;

import ar.edu.turnos.turnos.availability.domain.model.Occupancy;
import ar.edu.turnos.turnos.availability.domain.ports.in.GetOccupanciesUseCase;
import ar.edu.turnos.turnos.availability.domain.ports.out.OccupancySource;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GetOccupanciesUseCaseImpl implements GetOccupanciesUseCase {
    private final OccupancySource source;

    public GetOccupanciesUseCaseImpl(OccupancySource source) { this.source = source; }

    @Override
    public List<Occupancy> get(long professionalId, LocalDate date) {
        if (professionalId <= 0 || date == null) { throw new IllegalArgumentException("Turno inválido"); }
        return source.find(professionalId, date);
    }
}

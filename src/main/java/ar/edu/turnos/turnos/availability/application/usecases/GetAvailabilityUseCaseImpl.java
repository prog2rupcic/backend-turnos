package ar.edu.turnos.turnos.availability.application.usecases;

import ar.edu.turnos.turnos.availability.domain.model.Availability;
import ar.edu.turnos.turnos.availability.domain.model.AvailabilityCalculator;
import ar.edu.turnos.turnos.availability.domain.model.AvailabilitySourceException;
import ar.edu.turnos.turnos.availability.domain.model.ProfessionalAgenda;
import ar.edu.turnos.turnos.availability.domain.ports.in.GetAvailabilityUseCase;
import ar.edu.turnos.turnos.availability.domain.ports.out.OccupancySource;
import ar.edu.turnos.turnos.availability.domain.ports.out.ProfessionalAgendaSource;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GetAvailabilityUseCaseImpl implements GetAvailabilityUseCase {
    private final ProfessionalAgendaSource agendaSource;
    private final OccupancySource occupancySource;
    private final Clock clock;

    public GetAvailabilityUseCaseImpl(ProfessionalAgendaSource agendaSource, OccupancySource occupancySource, Clock clock) {
        this.agendaSource = agendaSource;
        this.occupancySource = occupancySource;
        this.clock = clock;
    }

    @Override
    public Availability get(long professionalId, LocalDate date) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (professionalId <= 0 || date == null || date.isBefore(now.toLocalDate())) {
            throw new IllegalArgumentException("Profesional o fecha inválidos");
        }
        ProfessionalAgenda agenda = agendaSource.get(professionalId);
        if (agenda == null || agenda.id() != professionalId || agenda.weeklySchedules() == null) {
            throw new AvailabilitySourceException(true);
        }
        if (!agenda.enabled() || !agenda.categoryEnabled() || agenda.weeklySchedules().isEmpty()) {
            return new Availability(professionalId, date, List.of());
        }
        try {
            return AvailabilityCalculator.calculate(agenda, date, occupancySource.find(professionalId, date), now);
        } catch (IllegalArgumentException exception) {
            throw new AvailabilitySourceException(true);
        }
    }
}

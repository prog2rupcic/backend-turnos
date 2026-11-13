package ar.edu.turnos.turnos.reservation.infrastructure.availability;

import ar.edu.turnos.turnos.availability.domain.ports.in.GetOccupanciesUseCase;
import ar.edu.turnos.turnos.reservation.domain.model.SlotOccupancy;
import ar.edu.turnos.turnos.reservation.domain.ports.out.SlotOccupancyLookup;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.stereotype.Component;

@Component
public class SlotOccupancyAdapter implements SlotOccupancyLookup {
    private final GetOccupanciesUseCase occupancies;

    public SlotOccupancyAdapter(GetOccupanciesUseCase occupancies) { this.occupancies = occupancies; }

    @Override
    public SlotOccupancy get(long professionalId, LocalDate date, LocalTime time) {
        var found = occupancies.get(professionalId, date).stream()
                .filter(item -> item.date().equals(date) && item.startTime().equals(time)).toList();
        if (found.stream().anyMatch(item -> item.status() == ar.edu.turnos.turnos.availability.domain.model.Occupancy.Status.CONFIRMED)) {
            return new SlotOccupancy(SlotOccupancy.Status.CONFIRMED, null);
        }
        return found.stream().filter(item -> item.status() == ar.edu.turnos.turnos.availability.domain.model.Occupancy.Status.HELD)
                .findFirst().map(item -> new SlotOccupancy(SlotOccupancy.Status.HELD, item.expiresAt()))
                .orElse(new SlotOccupancy(SlotOccupancy.Status.FREE, null));
    }
}

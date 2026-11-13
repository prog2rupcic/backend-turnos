package ar.edu.turnos.turnos.reservation.domain.ports.out;

import ar.edu.turnos.turnos.reservation.domain.model.SlotOccupancy;
import java.time.LocalDate;
import java.time.LocalTime;

public interface SlotOccupancyLookup {
    SlotOccupancy get(long professionalId, LocalDate date, LocalTime startTime);
}

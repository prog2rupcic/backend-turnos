package ar.edu.turnos.turnos.reservation.domain.ports.out;

import java.time.LocalDate;
import java.time.LocalTime;

public interface SlotAvailability {
    boolean isAvailable(long professionalId, LocalDate date, LocalTime startTime);
}

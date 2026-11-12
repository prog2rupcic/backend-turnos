package ar.edu.turnos.turnos.availability.domain.ports.in;

import java.time.LocalDate;
import java.time.LocalTime;

public interface IsSlotAvailableUseCase {
    enum Result { AVAILABLE, OCCUPIED, NOT_FOUND, SOURCE_UNAVAILABLE, INVALID_SOURCE_DATA, INVALID_REQUEST }

    Result check(long professionalId, LocalDate date, LocalTime startTime);
}

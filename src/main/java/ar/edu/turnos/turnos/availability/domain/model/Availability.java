package ar.edu.turnos.turnos.availability.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record Availability(long professionalId, LocalDate date, List<LocalTime> availableStartTimes) {
}

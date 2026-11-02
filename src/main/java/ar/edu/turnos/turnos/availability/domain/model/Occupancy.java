package ar.edu.turnos.turnos.availability.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;

public record Occupancy(LocalDate date, LocalTime startTime, Status status) {
    public enum Status { HELD, CONFIRMED }
}

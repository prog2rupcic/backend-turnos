package ar.edu.turnos.turnos.availability.domain.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;

public record Occupancy(LocalDate date, LocalTime startTime, Status status, Instant expiresAt) {
    public Occupancy(LocalDate date, LocalTime startTime, Status status) {
        this(date, startTime, status, null);
    }
    public enum Status { HELD, CONFIRMED }
}

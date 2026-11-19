package ar.edu.turnos.turnos.reservation.domain.model;

import java.time.Instant;

public record SlotOccupancy(Status status, Instant expiresAt) {
    public enum Status { FREE, HELD, CONFIRMED }
}

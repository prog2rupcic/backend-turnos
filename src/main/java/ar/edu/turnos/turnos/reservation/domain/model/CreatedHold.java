package ar.edu.turnos.turnos.reservation.domain.model;

import java.time.Instant;

public record CreatedHold(String holdId, String reservationProcessId, Instant expiresAt) {
}

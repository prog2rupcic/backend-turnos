package ar.edu.turnos.turnos.reservation.infrastructure.web.dto;

import java.time.Instant;

public record ConfirmHoldResponse(String id, String status, Instant expiresAt) {
}

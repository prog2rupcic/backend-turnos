package ar.edu.turnos.turnos.reservation.domain.model;

import java.time.Instant;

public record AdditionalInformationRequest(String eventId, String reservationProcessId, String holdId,
                                           String requiredField, String message, Instant expiresAt,
                                           Instant occurredAt) {
}

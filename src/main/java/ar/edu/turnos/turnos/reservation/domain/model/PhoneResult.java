package ar.edu.turnos.turnos.reservation.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record PhoneResult(String eventId, String groupId, String reservationProcessId, String holdId,
                          String requestEventId, Long reservationId, Long professionalId, LocalDate date,
                          LocalTime startTime, String reason, String message, Instant expiresAt,
                          Instant occurredAt) {
    public boolean confirmed() { return reservationId != null; }
}

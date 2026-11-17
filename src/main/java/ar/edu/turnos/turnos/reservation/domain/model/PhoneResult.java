package ar.edu.turnos.turnos.reservation.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record PhoneResult(String eventId, String groupId, String reservationProcessId, String holdId,
                          String requestEventId, Long reservationId, Long professionalId, LocalDate date,
                          LocalTime startTime, String reason, String message, Instant expiresAt,
                          Instant occurredAt, Type type) {
    public enum Type { CONFIRMED, REJECTED, EXPIRED, INVALID }

    public PhoneResult(String eventId, String groupId, String reservationProcessId, String holdId,
                       String requestEventId, Long reservationId, Long professionalId, LocalDate date,
                       LocalTime startTime, String reason, String message, Instant expiresAt, Instant occurredAt) {
        this(eventId, groupId, reservationProcessId, holdId, requestEventId, reservationId, professionalId,
                date, startTime, reason, message, expiresAt, occurredAt,
                reservationId == null ? Type.REJECTED : Type.CONFIRMED);
    }

    public boolean confirmed() { return type == Type.CONFIRMED; }
}

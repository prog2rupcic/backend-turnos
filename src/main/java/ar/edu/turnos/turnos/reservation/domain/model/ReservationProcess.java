package ar.edu.turnos.turnos.reservation.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record ReservationProcess(String id, long ownerUserId, long professionalId, LocalDate date,
                                 LocalTime startTime, Status status, String holdId, String reservationProcessId,
                                 Instant expiresAt, Instant createdAt, Instant updatedAt, Long version) {
    public enum Status { HOLD_REQUESTING, HELD, HOLD_OUTCOME_UNKNOWN, FAILED }

    public ReservationProcess withHold(String hold, String externalProcessId, Instant expiration, Instant now) {
        return new ReservationProcess(id, ownerUserId, professionalId, date, startTime, Status.HELD,
                hold, externalProcessId, expiration, createdAt, now, version);
    }

    public ReservationProcess withStatus(Status next, Instant now) {
        return new ReservationProcess(id, ownerUserId, professionalId, date, startTime, next,
                holdId, reservationProcessId, expiresAt, createdAt, now, version);
    }
}

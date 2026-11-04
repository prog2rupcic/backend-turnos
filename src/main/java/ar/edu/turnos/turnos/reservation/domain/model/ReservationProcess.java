package ar.edu.turnos.turnos.reservation.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record ReservationProcess(String id, long ownerUserId, long professionalId, LocalDate date,
                                 LocalTime startTime, Status status, String holdId, String reservationProcessId,
                                 Instant expiresAt, String requestEventId, String requestRequiredField,
                                 String requestMessage, Instant createdAt, Instant updatedAt, Long version) {
    public enum Status {
        HOLD_REQUESTING, HELD, HOLD_OUTCOME_UNKNOWN, CONFIRM_REQUESTING, CONFIRM_OUTCOME_UNKNOWN,
        WAITING_FOR_PHONE, EXPIRED, FAILED
    }

    public ReservationProcess withHold(String hold, String externalProcessId, Instant expiration, Instant now) {
        return new ReservationProcess(id, ownerUserId, professionalId, date, startTime, Status.HELD,
                hold, externalProcessId, expiration, requestEventId, requestRequiredField, requestMessage,
                createdAt, now, version);
    }

    public ReservationProcess withStatus(Status next, Instant now) {
        return new ReservationProcess(id, ownerUserId, professionalId, date, startTime, next,
                holdId, reservationProcessId, expiresAt, requestEventId, requestRequiredField, requestMessage,
                createdAt, now, version);
    }

    public ReservationProcess waitingForPhone(Instant expiration, Instant now) {
        return new ReservationProcess(id, ownerUserId, professionalId, date, startTime, Status.WAITING_FOR_PHONE,
                holdId, reservationProcessId, expiration, requestEventId, requestRequiredField, requestMessage,
                createdAt, now, version);
    }

    public ReservationProcess withPhoneRequest(AdditionalInformationRequest request, Instant now) {
        Instant expiration = request.expiresAt() == null ? expiresAt : request.expiresAt();
        return new ReservationProcess(id, ownerUserId, professionalId, date, startTime, Status.WAITING_FOR_PHONE,
                holdId, reservationProcessId, expiration, request.eventId(), request.requiredField(),
                request.message(), createdAt, now, version);
    }
}

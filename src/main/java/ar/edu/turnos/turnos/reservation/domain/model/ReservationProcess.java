package ar.edu.turnos.turnos.reservation.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record ReservationProcess(String id, long ownerUserId, long professionalId, LocalDate date,
                                 LocalTime startTime, Status status, String holdId, String reservationProcessId,
                                 Instant expiresAt, String requestEventId, String requestRequiredField,
                                 String requestMessage, Instant createdAt, Instant updatedAt, Long version,
                                 Long reservationId, Instant confirmedAt, String rejectionReason,
                                 String terminalReason) {
    public ReservationProcess(String id, long ownerUserId, long professionalId, LocalDate date,
                              LocalTime startTime, Status status, String holdId, String reservationProcessId,
                              Instant expiresAt, String requestEventId, String requestRequiredField,
                              String requestMessage, Instant createdAt, Instant updatedAt, Long version) {
        this(id, ownerUserId, professionalId, date, startTime, status, holdId, reservationProcessId, expiresAt,
                requestEventId, requestRequiredField, requestMessage, createdAt, updatedAt, version,
                null, null, null, null);
    }
    public enum Status {
        HOLD_REQUESTING, HELD, HOLD_OUTCOME_UNKNOWN, CONFIRM_REQUESTING, CONFIRM_OUTCOME_UNKNOWN,
        WAITING_FOR_PHONE, PHONE_SUBMISSION_PENDING, PHONE_SUBMITTED, CONFIRMED, EXPIRED, INVALID, FAILED
    }

    public ReservationProcess withHold(String hold, String externalProcessId, Instant expiration, Instant now) {
        return new ReservationProcess(id, ownerUserId, professionalId, date, startTime, Status.HELD,
                hold, externalProcessId, expiration, requestEventId, requestRequiredField, requestMessage,
                createdAt, now, version, reservationId, confirmedAt, rejectionReason, terminalReason);
    }

    public ReservationProcess withStatus(Status next, Instant now) {
        return new ReservationProcess(id, ownerUserId, professionalId, date, startTime, next,
                holdId, reservationProcessId, expiresAt, requestEventId, requestRequiredField, requestMessage,
                createdAt, now, version, reservationId, confirmedAt, rejectionReason, terminalReason);
    }

    public ReservationProcess waitingForPhone(Instant expiration, Instant now) {
        return new ReservationProcess(id, ownerUserId, professionalId, date, startTime, Status.WAITING_FOR_PHONE,
                holdId, reservationProcessId, expiration, requestEventId, requestRequiredField, requestMessage,
                createdAt, now, version, reservationId, confirmedAt, rejectionReason, terminalReason);
    }

    public ReservationProcess withPhoneRequest(AdditionalInformationRequest request, Instant now) {
        Instant expiration = request.expiresAt() == null ? expiresAt : request.expiresAt();
        return new ReservationProcess(id, ownerUserId, professionalId, date, startTime, Status.WAITING_FOR_PHONE,
                holdId, reservationProcessId, expiration, request.eventId(), request.requiredField(),
                request.message(), createdAt, now, version, reservationId, confirmedAt, rejectionReason, terminalReason);
    }

    public ReservationProcess confirmed(long externalId, Instant occurredAt, Instant now) {
        return new ReservationProcess(id, ownerUserId, professionalId, date, startTime, Status.CONFIRMED,
                holdId, reservationProcessId, expiresAt, requestEventId, requestRequiredField, requestMessage,
                createdAt, now, version, externalId, occurredAt, null, null);
    }

    public ReservationProcess rejected(String reason, String message, Instant expiration, Instant now) {
        return new ReservationProcess(id, ownerUserId, professionalId, date, startTime, Status.WAITING_FOR_PHONE,
                holdId, reservationProcessId, expiration, requestEventId, requestRequiredField, message,
                createdAt, now, version, reservationId, confirmedAt, reason, terminalReason);
    }

    public ReservationProcess terminal(Status terminalStatus, String reason, Instant now) {
        if (terminalStatus != Status.EXPIRED && terminalStatus != Status.INVALID) {
            throw new IllegalArgumentException("Estado terminal de evento inválido");
        }
        return new ReservationProcess(id, ownerUserId, professionalId, date, startTime, terminalStatus,
                holdId, reservationProcessId, expiresAt, requestEventId, requestRequiredField, requestMessage,
                createdAt, now, version, reservationId, confirmedAt, rejectionReason, reason);
    }
}

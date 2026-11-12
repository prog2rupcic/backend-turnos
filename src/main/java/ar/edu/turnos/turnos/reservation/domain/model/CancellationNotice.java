package ar.edu.turnos.turnos.reservation.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record CancellationNotice(String eventId, String reservationProcessId, String holdId,
                                 String requestEventId, long reservationId, long professionalId,
                                 LocalDate date, LocalTime startTime, String reason, Instant occurredAt) { }

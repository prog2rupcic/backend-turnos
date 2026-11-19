package ar.edu.turnos.turnos.reservation.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record OwnReservation(String processId, long reservationId, long professionalId, LocalDate date,
                             LocalTime startTime, LocalTime endTime, String professionalFirstName,
                             String professionalLastName, String status, Instant confirmedAt,
                             Instant cancelledAt, String cancellationReason) { }

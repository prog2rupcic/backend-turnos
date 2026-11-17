package ar.edu.turnos.turnos.reservation.domain.model;

import java.time.Instant;

public record CancellationReceipt(long reservationId, String reservationProcessId, Instant cancelledAt) { }

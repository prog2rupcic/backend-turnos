package ar.edu.turnos.turnos.reservation.infrastructure.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record CreateHoldResponse(String id, long professionalId, LocalDate date, LocalTime startTime,
                                 String status, String holdId, String reservationProcessId, Instant expiresAt) { }

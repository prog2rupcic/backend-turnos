package ar.edu.turnos.turnos.reservation.infrastructure.web.dto;

import jakarta.validation.constraints.Size;

public record CancelReservationRequest(@Size(max = 500) String reason) { }

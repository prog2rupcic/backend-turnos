package ar.edu.turnos.turnos.reservation.domain.model;

import java.util.List;

public record ReservationPage(List<OwnReservation> content, long totalElements, int page, int size) { }

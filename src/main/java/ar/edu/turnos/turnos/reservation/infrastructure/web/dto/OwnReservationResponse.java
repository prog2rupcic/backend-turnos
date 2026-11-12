package ar.edu.turnos.turnos.reservation.infrastructure.web.dto;

import ar.edu.turnos.turnos.reservation.domain.model.OwnReservation;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record OwnReservationResponse(String processId, long reservationId, long professionalId, LocalDate date,
                                     LocalTime startTime, LocalTime endTime, String professionalFirstName,
                                     String professionalLastName, String status, Instant confirmedAt,
                                     Instant cancelledAt, String cancellationReason) {
    public static OwnReservationResponse from(OwnReservation own) {
        return new OwnReservationResponse(own.processId(), own.reservationId(), own.professionalId(), own.date(),
                own.startTime(), own.endTime(), own.professionalFirstName(), own.professionalLastName(),
                own.status(), own.confirmedAt(), own.cancelledAt(), own.cancellationReason());
    }
}

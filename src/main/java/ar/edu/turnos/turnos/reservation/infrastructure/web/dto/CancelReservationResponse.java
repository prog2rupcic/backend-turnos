package ar.edu.turnos.turnos.reservation.infrastructure.web.dto;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import java.time.Instant;

public record CancelReservationResponse(String processId, String status, Instant cancelledAt,
                                        String cancellationReason) {
    public static CancelReservationResponse from(ReservationProcess process) {
        return new CancelReservationResponse(process.id(), process.status().name(), process.cancelledAt(),
                process.cancellationReason());
    }
}

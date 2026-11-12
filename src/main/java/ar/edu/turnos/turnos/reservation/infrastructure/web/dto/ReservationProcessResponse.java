package ar.edu.turnos.turnos.reservation.infrastructure.web.dto;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcessView;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record ReservationProcessResponse(String id, String status, long professionalId, LocalDate date,
                                         LocalTime startTime, Instant expiresAt, boolean canSubmitPhone,
                                         String message, String rejectionReason, Long reservationId,
                                         Instant confirmedAt, String deliveryStatus, String terminalReason,
                                         Instant cancelledAt, String cancellationReason) {
    public static ReservationProcessResponse from(ReservationProcessView view) {
        var p = view.process();
        return new ReservationProcessResponse(p.id(), p.status().name(), p.professionalId(), p.date(), p.startTime(),
                p.expiresAt(), view.canSubmitPhone(), p.requestMessage(), p.rejectionReason(), p.reservationId(),
                p.confirmedAt(), view.deliveryStatus() == null ? null : view.deliveryStatus().name(),
                p.terminalReason(), p.cancelledAt(), p.cancellationReason());
    }
}

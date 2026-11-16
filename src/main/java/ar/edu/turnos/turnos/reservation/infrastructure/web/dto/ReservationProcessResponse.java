package ar.edu.turnos.turnos.reservation.infrastructure.web.dto;

import ar.edu.turnos.turnos.reservation.domain.model.RecoveryProgress;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcessView;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record ReservationProcessResponse(String id, String status, long professionalId, LocalDate date,
                                         LocalTime startTime, Instant expiresAt, boolean canSubmitPhone,
                                         String message, String rejectionReason, Long reservationId,
                                         Instant confirmedAt, String deliveryStatus, String terminalReason,
                                         Instant cancelledAt, String cancellationReason, String recoveryState,
                                         Integer recoveryAttempts, Instant nextRecoveryAt, String lastRecoveryErrorCode) {
    public static ReservationProcessResponse from(ReservationProcessView view) {
        var p = view.process();
        var recovery = view.recovery();
        // A reviewed process no longer has an automatic next attempt.
        var nextAttempt = recovery == null || recovery.state() == RecoveryProgress.State.REVIEW
                ? null : recovery.nextAttemptAt();
        return new ReservationProcessResponse(p.id(), p.status().name(), p.professionalId(), p.date(), p.startTime(),
                p.expiresAt(), view.canSubmitPhone(), p.requestMessage(), p.rejectionReason(), p.reservationId(),
                p.confirmedAt(), view.deliveryStatus() == null ? null : view.deliveryStatus().name(),
                p.terminalReason(), p.cancelledAt(), p.cancellationReason(),
                recovery == null ? null : recovery.state().name(),
                recovery == null ? null : recovery.attempts(),
                nextAttempt,
                recovery == null ? null : recovery.lastErrorCode());
    }
}

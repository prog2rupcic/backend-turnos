package ar.edu.turnos.turnos.reservation.application.usecases;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationOperationException;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.ports.in.CancelOwnReservationUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.in.CancellationProgressUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.out.CourseReservationsGateway;
import org.springframework.stereotype.Component;

@Component
public class CancelOwnReservationUseCaseImpl implements CancelOwnReservationUseCase {
    private final CancellationProgressUseCase progress;
    private final CourseReservationsGateway course;

    public CancelOwnReservationUseCaseImpl(CancellationProgressUseCase progress, CourseReservationsGateway course) {
        this.progress = progress;
        this.course = course;
    }

    @Override
    public ReservationProcess cancel(String id, long ownerUserId, String reason) {
        if (id == null || id.isBlank() || ownerUserId <= 0 || reason != null && reason.length() > 500) {
            throw new IllegalArgumentException("Cancelación inválida");
        }
        var process = progress.begin(id, ownerUserId, reason);
        if (process.status() == ReservationProcess.Status.CANCELLED) { return process; }
        try {
            var receipt = course.cancel(process.reservationProcessId(), reason);
            return progress.complete(id, receipt, reason);
        } catch (ReservationOperationException exception) {
            if (exception.reason() == ReservationOperationException.Reason.INTEGRATION_UNAVAILABLE) {
                progress.rejected(id);
            } else {
                progress.uncertain(id);
            }
            throw exception;
        }
    }
}

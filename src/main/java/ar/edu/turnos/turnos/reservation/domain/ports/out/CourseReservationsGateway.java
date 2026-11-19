package ar.edu.turnos.turnos.reservation.domain.ports.out;

import ar.edu.turnos.turnos.reservation.domain.model.CancellationReceipt;
import ar.edu.turnos.turnos.reservation.domain.model.OwnReservation;
import java.util.List;

public interface CourseReservationsGateway {
    record Page(List<OwnReservation> content, long totalElements, int returnedCount) { }
    Page list(String externalPatientId, int page, int size);
    CancellationReceipt cancel(String reservationProcessId, String reason);
}

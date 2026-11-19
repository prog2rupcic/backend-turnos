package ar.edu.turnos.turnos.reservation.infrastructure.web.dto;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationPage;
import java.util.List;

public record OwnReservationsResponse(List<OwnReservationResponse> content, long totalElements, int page, int size) {
    public static OwnReservationsResponse from(ReservationPage result) {
        return new OwnReservationsResponse(result.content().stream().map(OwnReservationResponse::from).toList(),
                result.totalElements(), result.page(), result.size());
    }
}

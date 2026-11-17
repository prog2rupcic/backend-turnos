package ar.edu.turnos.turnos.reservation.infrastructure.web.controller;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationOperationException;
import ar.edu.turnos.turnos.reservation.domain.ports.in.CancelOwnReservationUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.in.ListOwnReservationsUseCase;
import ar.edu.turnos.turnos.reservation.infrastructure.web.dto.CancelReservationRequest;
import ar.edu.turnos.turnos.reservation.infrastructure.web.dto.CancelReservationResponse;
import ar.edu.turnos.turnos.reservation.infrastructure.web.dto.OwnReservationsResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
public class OwnReservationsController {
    private final ListOwnReservationsUseCase list;
    private final CancelOwnReservationUseCase cancel;

    public OwnReservationsController(ListOwnReservationsUseCase list, CancelOwnReservationUseCase cancel) {
        this.list = list;
        this.cancel = cancel;
    }

    @GetMapping
    public OwnReservationsResponse list(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @AuthenticationPrincipal Jwt jwt) {
        return OwnReservationsResponse.from(list.list(owner(jwt), page, size));
    }

    @PostMapping("/{processId}/cancel")
    public CancelReservationResponse cancel(@PathVariable String processId,
                                             @Valid @RequestBody(required = false) CancelReservationRequest body,
                                             @AuthenticationPrincipal Jwt jwt) {
        return CancelReservationResponse.from(cancel.cancel(processId, owner(jwt),
                body == null ? null : body.reason()));
    }

    @ExceptionHandler(ReservationOperationException.class)
    ProblemDetail operationError(ReservationOperationException exception) {
        HttpStatus status = switch (exception.reason()) {
            case PROCESS_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NOT_OWNER -> HttpStatus.FORBIDDEN;
            case INVALID_STATE, CANCELLATION_IN_PROGRESS -> HttpStatus.CONFLICT;
            case INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case INTEGRATION_UNAVAILABLE, OUTCOME_UNKNOWN -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        var detail = ProblemDetail.forStatusAndDetail(status, "No se pudo consultar o cancelar la reserva");
        detail.setProperty("code", exception.reason().name());
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid() {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Parámetros inválidos");
        detail.setProperty("code", "INVALID_RESERVATION_REQUEST");
        return detail;
    }

    private static long owner(Jwt jwt) { return Long.parseLong(jwt.getSubject().substring("user-".length())); }
}

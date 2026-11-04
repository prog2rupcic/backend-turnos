package ar.edu.turnos.turnos.reservation.infrastructure.web.controller;

import ar.edu.turnos.turnos.reservation.domain.model.PhoneExchangeException;
import ar.edu.turnos.turnos.reservation.domain.ports.in.GetReservationProcessUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.in.SubmitPhoneUseCase;
import ar.edu.turnos.turnos.reservation.infrastructure.web.dto.ReservationProcessResponse;
import ar.edu.turnos.turnos.reservation.infrastructure.web.dto.SubmitPhoneRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservation-processes")
public class PhoneExchangeController {
    private final GetReservationProcessUseCase query;
    private final SubmitPhoneUseCase submit;

    public PhoneExchangeController(GetReservationProcessUseCase query, SubmitPhoneUseCase submit) {
        this.query = query;
        this.submit = submit;
    }

    @GetMapping("/{id}")
    public ReservationProcessResponse get(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return ReservationProcessResponse.from(query.get(id, owner(jwt)));
    }

    @PostMapping("/{id}/phone")
    public ResponseEntity<ReservationProcessResponse> phone(@PathVariable String id,
            @Valid @RequestBody SubmitPhoneRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.accepted().body(ReservationProcessResponse.from(submit.submit(id, owner(jwt),
                request.phoneNumber())));
    }

    @ExceptionHandler(PhoneExchangeException.class)
    ProblemDetail error(PhoneExchangeException exception) {
        var status = switch (exception.reason()) {
            case PROCESS_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PROCESS_NOT_OWNED -> HttpStatus.FORBIDDEN;
            case INTEGRATION_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.CONFLICT;
        };
        var detail = ProblemDetail.forStatusAndDetail(status, "No se puede realizar la operación sobre el proceso");
        detail.setProperty("code", exception.reason().name());
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid() {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Teléfono o identificador inválido");
        detail.setProperty("code", "INVALID_PHONE_REQUEST");
        return detail;
    }

    private static long owner(Jwt jwt) { return Long.parseLong(jwt.getSubject().substring("user-".length())); }
}

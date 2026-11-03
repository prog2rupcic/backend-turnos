package ar.edu.turnos.turnos.reservation.infrastructure.web.controller;

import ar.edu.turnos.turnos.reservation.application.exception.HoldCreationException;
import ar.edu.turnos.turnos.reservation.application.service.ReservationService;
import ar.edu.turnos.turnos.reservation.infrastructure.web.dto.CreateHoldRequest;
import ar.edu.turnos.turnos.reservation.infrastructure.web.dto.CreateHoldResponse;
import ar.edu.turnos.turnos.reservation.infrastructure.web.mapper.ReservationDtoMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservation-processes")
public class ReservationController {
    private final ReservationService reservationService;
    private final ReservationDtoMapper mapper;

    public ReservationController(ReservationService reservationService, ReservationDtoMapper mapper) {
        this.reservationService = reservationService;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<CreateHoldResponse> create(@Valid @RequestBody CreateHoldRequest request,
                                                      @AuthenticationPrincipal Jwt jwt) {
        // El JwtDecoder valida el formato user-{id} antes de llegar al controlador.
        long userId = Long.parseLong(jwt.getSubject().substring("user-".length()));
        if (request.startTime().getSecond() != 0 || request.startTime().getNano() != 0) {
            throw new IllegalArgumentException("El horario debe tener precisión de minutos");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(
                reservationService.createHold(userId, request.professionalId(), request.date(), request.startTime())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidRequest() {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_SLOT", "Datos del turno inválidos");
    }

    @ExceptionHandler(HoldCreationException.class)
    ProblemDetail holdError(HoldCreationException exception) {
        HttpStatus status = switch (exception.reason()) {
            case SLOT_UNAVAILABLE, ALREADY_IN_PROGRESS -> HttpStatus.CONFLICT;
            case PROFESSIONAL_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INTEGRATION_UNAVAILABLE, OUTCOME_UNKNOWN -> HttpStatus.SERVICE_UNAVAILABLE;
            case INVALID_RESPONSE -> HttpStatus.BAD_GATEWAY;
        };
        ProblemDetail result = problem(status, exception.reason().name(), "No se pudo asegurar el turno");
        if (exception.processId() != null) {
            result.setProperty("processId", exception.processId());
        }
        return result;
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setProperty("code", code);
        return result;
    }
}

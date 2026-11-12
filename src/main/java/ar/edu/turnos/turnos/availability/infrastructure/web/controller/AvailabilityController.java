package ar.edu.turnos.turnos.availability.infrastructure.web.controller;

import ar.edu.turnos.turnos.availability.domain.model.AvailabilitySourceException;
import ar.edu.turnos.turnos.availability.domain.model.ProfessionalNotFoundException;
import ar.edu.turnos.turnos.availability.domain.ports.in.GetAvailabilityUseCase;
import ar.edu.turnos.turnos.availability.infrastructure.web.dto.AvailabilityResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/professionals")
public class AvailabilityController {
    private final GetAvailabilityUseCase getAvailability;

    public AvailabilityController(GetAvailabilityUseCase getAvailability) {
        this.getAvailability = getAvailability;
    }

    @GetMapping("/{id}/availability")
    public AvailabilityResponse get(@PathVariable long id,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return AvailabilityResponse.from(getAvailability.get(id, date));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidRequest() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Profesional o fecha inválidos");
    }

    @ExceptionHandler(ProfessionalNotFoundException.class)
    ProblemDetail notFound() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Profesional inexistente");
    }

    @ExceptionHandler(AvailabilitySourceException.class)
    ProblemDetail unavailable(AvailabilitySourceException exception) {
        HttpStatus status = exception.invalidResponse() ? HttpStatus.BAD_GATEWAY : HttpStatus.SERVICE_UNAVAILABLE;
        return ProblemDetail.forStatusAndDetail(status, "No se pudo verificar la disponibilidad");
    }
}

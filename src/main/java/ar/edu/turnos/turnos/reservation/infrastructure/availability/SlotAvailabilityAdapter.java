package ar.edu.turnos.turnos.reservation.infrastructure.availability;

import ar.edu.turnos.turnos.availability.domain.ports.in.IsSlotAvailableUseCase;
import ar.edu.turnos.turnos.reservation.application.exception.HoldCreationException;
import ar.edu.turnos.turnos.reservation.domain.ports.out.SlotAvailability;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.stereotype.Component;

@Component
public class SlotAvailabilityAdapter implements SlotAvailability {
    private final IsSlotAvailableUseCase checker;

    public SlotAvailabilityAdapter(IsSlotAvailableUseCase checker) {
        this.checker = checker;
    }

    @Override
    public boolean isAvailable(long professionalId, LocalDate date, LocalTime startTime) {
        return switch (checker.check(professionalId, date, startTime)) {
            case AVAILABLE -> true;
            case OCCUPIED -> false;
            case NOT_FOUND -> throw new HoldCreationException(HoldCreationException.Reason.PROFESSIONAL_NOT_FOUND);
            case SOURCE_UNAVAILABLE -> throw new HoldCreationException(HoldCreationException.Reason.INTEGRATION_UNAVAILABLE);
            case INVALID_SOURCE_DATA -> throw new HoldCreationException(HoldCreationException.Reason.INVALID_RESPONSE);
            case INVALID_REQUEST -> throw new IllegalArgumentException("Datos del turno inválidos");
        };
    }
}

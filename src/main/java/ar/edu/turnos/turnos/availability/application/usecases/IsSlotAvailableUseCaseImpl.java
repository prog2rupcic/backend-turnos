package ar.edu.turnos.turnos.availability.application.usecases;

import ar.edu.turnos.turnos.availability.domain.ports.in.GetAvailabilityUseCase;
import ar.edu.turnos.turnos.availability.domain.ports.in.IsSlotAvailableUseCase;
import ar.edu.turnos.turnos.availability.domain.model.AvailabilitySourceException;
import ar.edu.turnos.turnos.availability.domain.model.ProfessionalNotFoundException;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.stereotype.Component;

@Component
public class IsSlotAvailableUseCaseImpl implements IsSlotAvailableUseCase {
    private final GetAvailabilityUseCase getAvailability;

    public IsSlotAvailableUseCaseImpl(GetAvailabilityUseCase getAvailability) {
        this.getAvailability = getAvailability;
    }

    @Override
    public Result check(long professionalId, LocalDate date, LocalTime startTime) {
        if (startTime == null) {
            return Result.INVALID_REQUEST;
        }
        try {
            return getAvailability.get(professionalId, date).availableStartTimes().contains(startTime)
                    ? Result.AVAILABLE : Result.OCCUPIED;
        } catch (ProfessionalNotFoundException _) {
            return Result.NOT_FOUND;
        } catch (AvailabilitySourceException exception) {
            return exception.invalidResponse() ? Result.INVALID_SOURCE_DATA : Result.SOURCE_UNAVAILABLE;
        } catch (IllegalArgumentException _) {
            return Result.INVALID_REQUEST;
        }
    }
}

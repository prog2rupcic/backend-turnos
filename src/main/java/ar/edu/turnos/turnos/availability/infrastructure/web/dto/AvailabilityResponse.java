package ar.edu.turnos.turnos.availability.infrastructure.web.dto;

import ar.edu.turnos.turnos.availability.domain.model.Availability;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AvailabilityResponse(long professionalId, LocalDate date, List<LocalTime> availableStartTimes) {
    public static AvailabilityResponse from(Availability availability) {
        return new AvailabilityResponse(availability.professionalId(), availability.date(),
                availability.availableStartTimes());
    }
}

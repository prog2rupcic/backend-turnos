package ar.edu.turnos.turnos.availability.domain.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record ProfessionalAgenda(long id, boolean enabled, boolean categoryEnabled, List<WeeklySchedule> weeklySchedules) {
    public record WeeklySchedule(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
                                 int slotDurationMinutes) {
    }
}

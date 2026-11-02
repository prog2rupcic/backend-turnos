package ar.edu.turnos.turnos.availability.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class AvailabilityCalculator {
    private AvailabilityCalculator() {
    }

    public static Availability calculate(ProfessionalAgenda agenda, LocalDate date, List<Occupancy> occupancies,
                                         LocalDateTime now) {
        if (agenda.weeklySchedules() == null || occupancies == null) {
            throw new IllegalArgumentException("Datos de disponibilidad incompletos");
        }
        Set<LocalTime> occupied = new TreeSet<>();
        for (Occupancy occupancy : occupancies) {
            if (occupancy == null || !date.equals(occupancy.date()) || occupancy.startTime() == null
                    || occupancy.status() == null) {
                throw new IllegalArgumentException("Ocupación inválida");
            }
            occupied.add(occupancy.startTime());
        }
        Set<LocalTime> available = new TreeSet<>();
        if (!agenda.enabled() || !agenda.categoryEnabled()) {
            return new Availability(agenda.id(), date, List.of());
        }
        for (ProfessionalAgenda.WeeklySchedule schedule : agenda.weeklySchedules()) {
            if (schedule == null || schedule.dayOfWeek() == null || schedule.startTime() == null
                    || schedule.endTime() == null || !schedule.startTime().isBefore(schedule.endTime())
                    || schedule.slotDurationMinutes() <= 0 || schedule.slotDurationMinutes() > 1440) {
                throw new IllegalArgumentException("Horario semanal inválido");
            }
            if (schedule.dayOfWeek() != date.getDayOfWeek()) {
                continue;
            }
            long stepSeconds = schedule.slotDurationMinutes() * 60L;
            for (long second = schedule.startTime().toSecondOfDay();
                 second + stepSeconds <= schedule.endTime().toSecondOfDay(); second += stepSeconds) {
                LocalTime start = LocalTime.ofSecondOfDay(second);
                if (!occupied.contains(start) && (date.isAfter(now.toLocalDate())
                        || start.isAfter(now.toLocalTime()))) {
                    available.add(start);
                }
            }
        }
        return new Availability(agenda.id(), date, List.copyOf(available));
    }
}

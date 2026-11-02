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
        validateInputs(agenda, occupancies);
        if (!agenda.enabled() || !agenda.categoryEnabled()) {
            return new Availability(agenda.id(), date, List.of());
        }
        Set<LocalTime> occupied = occupiedStartTimes(date, occupancies);
        Set<LocalTime> available = new TreeSet<>();
        for (ProfessionalAgenda.WeeklySchedule schedule : agenda.weeklySchedules()) {
            addSlots(schedule, date, occupied, now, available);
        }
        return new Availability(agenda.id(), date, List.copyOf(available));
    }

    private static void validateInputs(ProfessionalAgenda agenda, List<Occupancy> occupancies) {
        if (agenda.weeklySchedules() == null || occupancies == null) {
            throw new IllegalArgumentException("Datos de disponibilidad incompletos");
        }
    }

    private static Set<LocalTime> occupiedStartTimes(LocalDate date, List<Occupancy> occupancies) {
        Set<LocalTime> occupied = new TreeSet<>();
        for (Occupancy occupancy : occupancies) {
            if (occupancy == null || !date.equals(occupancy.date()) || occupancy.startTime() == null
                    || occupancy.status() == null) {
                throw new IllegalArgumentException("Ocupación inválida");
            }
            occupied.add(occupancy.startTime());
        }
        return occupied;
    }

    private static void addSlots(ProfessionalAgenda.WeeklySchedule schedule, LocalDate date, Set<LocalTime> occupied,
                                 LocalDateTime now, Set<LocalTime> available) {
        validateSchedule(schedule);
        if (!schedule.dayOfWeek().equals(date.getDayOfWeek())) {
            return;
        }
        long stepSeconds = schedule.slotDurationMinutes() * 60L;
        for (long second = schedule.startTime().toSecondOfDay();
             second + stepSeconds <= schedule.endTime().toSecondOfDay(); second += stepSeconds) {
            LocalTime start = LocalTime.ofSecondOfDay(second);
            if (!occupied.contains(start) && isFuture(date, start, now)) {
                available.add(start);
            }
        }
    }

    private static void validateSchedule(ProfessionalAgenda.WeeklySchedule schedule) {
        if (schedule == null || schedule.dayOfWeek() == null || schedule.startTime() == null
                || schedule.endTime() == null || !schedule.startTime().isBefore(schedule.endTime())
                || schedule.slotDurationMinutes() <= 0 || schedule.slotDurationMinutes() > 1440) {
            throw new IllegalArgumentException("Horario semanal inválido");
        }
    }

    private static boolean isFuture(LocalDate date, LocalTime start, LocalDateTime now) {
        return date.isAfter(now.toLocalDate()) || start.isAfter(now.toLocalTime());
    }
}

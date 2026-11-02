package ar.edu.turnos.turnos.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.turnos.turnos.availability.application.usecases.GetAvailabilityUseCaseImpl;
import ar.edu.turnos.turnos.availability.domain.model.AvailabilityCalculator;
import ar.edu.turnos.turnos.availability.domain.model.Occupancy;
import ar.edu.turnos.turnos.availability.domain.model.ProfessionalAgenda;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvailabilityCalculatorTests {
    private final LocalDate monday = LocalDate.of(2026, 11, 9);
    private final ProfessionalAgenda.WeeklySchedule morning = new ProfessionalAgenda.WeeklySchedule(
            DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), 30);

    @Test
    void removesHeldAndConfirmedSlotsAndDeduplicatesOverlappingSchedules() {
        var agenda = new ProfessionalAgenda(15, true, true, List.of(morning,
                new ProfessionalAgenda.WeeklySchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0),
                        LocalTime.of(10, 0), 30),
                new ProfessionalAgenda.WeeklySchedule(DayOfWeek.TUESDAY, LocalTime.of(9, 0),
                        LocalTime.of(11, 0), 30)));
        var occupied = List.of(new Occupancy(monday, LocalTime.of(9, 30), Occupancy.Status.HELD),
                new Occupancy(monday, LocalTime.of(10, 0), Occupancy.Status.CONFIRMED));

        var result = AvailabilityCalculator.calculate(agenda, monday, occupied,
                monday.minusDays(1).atStartOfDay());

        assertThat(result.availableStartTimes()).containsExactly(LocalTime.of(9, 0),
                LocalTime.of(10, 30));
    }

    @Test
    void excludesPastStartsOnTodayUsingConfiguredZone() {
        var clock = Clock.fixed(Instant.parse("2026-11-09T12:15:00Z"),
                ZoneId.of("America/Argentina/Buenos_Aires"));
        var agenda = new ProfessionalAgenda(15, true, true, List.of(morning));
        var useCase = new GetAvailabilityUseCaseImpl(id -> agenda, (id, date) -> List.of(), clock);

        assertThat(useCase.get(15, monday).availableStartTimes()).containsExactly(LocalTime.of(9, 30),
                LocalTime.of(10, 0), LocalTime.of(10, 30));
        assertThatThrownBy(() -> useCase.get(15, monday.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disabledCategoryHasNoSlotsWithoutQueryingOccupancies() {
        var agenda = new ProfessionalAgenda(15, true, false, List.of(morning));
        var clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"),
                ZoneId.of("America/Argentina/Buenos_Aires"));
        var useCase = new GetAvailabilityUseCaseImpl(id -> agenda,
                (id, date) -> { throw new AssertionError("No se deben consultar ocupaciones"); }, clock);

        assertThat(useCase.get(15, monday).availableStartTimes()).isEmpty();
    }

    @Test
    void rejectsMalformedSchedulesInsteadOfLoopingOrOfferingSlots() {
        var invalid = new ProfessionalAgenda.WeeklySchedule(DayOfWeek.MONDAY, LocalTime.of(9, 0),
                LocalTime.of(11, 0), 1441);
        var agenda = new ProfessionalAgenda(15, true, true, List.of(invalid));
        assertThatThrownBy(() -> AvailabilityCalculator.calculate(agenda, monday, List.of(),
                LocalDateTime.of(2026, 11, 2, 8, 0))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AvailabilityCalculator.calculate(agenda, monday,
                List.of(new Occupancy(monday.minusDays(1), LocalTime.NOON, Occupancy.Status.HELD)),
                LocalDateTime.of(2026, 11, 2, 8, 0))).isInstanceOf(IllegalArgumentException.class);
    }
}

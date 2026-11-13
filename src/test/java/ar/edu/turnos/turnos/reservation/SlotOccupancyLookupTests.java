package ar.edu.turnos.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.turnos.turnos.availability.application.usecases.GetOccupanciesUseCaseImpl;
import ar.edu.turnos.turnos.availability.domain.model.Occupancy;
import ar.edu.turnos.turnos.availability.domain.ports.out.OccupancySource;
import ar.edu.turnos.turnos.reservation.domain.model.SlotOccupancy;
import ar.edu.turnos.turnos.reservation.infrastructure.availability.SlotOccupancyAdapter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlotOccupancyLookupTests {
    private static final LocalDate DATE = LocalDate.of(2026, 12, 7);
    private static final LocalTime TIME = LocalTime.of(9, 30);
    private final OccupancySource source = mock(OccupancySource.class);

    @Test
    void aConfirmedOccupancyPreventsAnyOtherInterpretation() {
        when(source.find(15, DATE)).thenReturn(List.of(
                new Occupancy(DATE, TIME, Occupancy.Status.HELD, Instant.parse("2026-12-07T15:00:00Z")),
                new Occupancy(DATE, TIME, Occupancy.Status.CONFIRMED, null)));

        var result = adapter().get(15, DATE, TIME);

        assertThat(result.status()).isEqualTo(SlotOccupancy.Status.CONFIRMED);
        assertThat(result.expiresAt()).isNull();
    }

    @Test
    void aHeldOccupancyKeepsItsRealExpiry() {
        var expiry = Instant.parse("2026-12-07T15:00:00Z");
        when(source.find(15, DATE)).thenReturn(List.of(new Occupancy(DATE, TIME, Occupancy.Status.HELD, expiry)));

        var result = adapter().get(15, DATE, TIME);

        assertThat(result.status()).isEqualTo(SlotOccupancy.Status.HELD);
        assertThat(result.expiresAt()).isEqualTo(expiry);
    }

    @Test
    void occupanciesOfOtherSlotsAreIgnored() {
        when(source.find(15, DATE)).thenReturn(List.of(
                new Occupancy(DATE, LocalTime.of(10, 0), Occupancy.Status.CONFIRMED, null),
                new Occupancy(DATE.plusDays(1), TIME, Occupancy.Status.CONFIRMED, null)));

        assertThat(adapter().get(15, DATE, TIME).status()).isEqualTo(SlotOccupancy.Status.FREE);
    }

    @Test
    void invalidOccupancyRequestsAreRejectedBeforeCallingTheCourse() {
        var useCase = new GetOccupanciesUseCaseImpl(source);

        assertThatThrownBy(() -> useCase.get(0, DATE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.get(15, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private SlotOccupancyAdapter adapter() {
        return new SlotOccupancyAdapter(new GetOccupanciesUseCaseImpl(source));
    }
}

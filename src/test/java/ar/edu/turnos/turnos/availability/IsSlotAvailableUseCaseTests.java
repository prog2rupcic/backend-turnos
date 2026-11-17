package ar.edu.turnos.turnos.availability;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.turnos.turnos.availability.application.usecases.IsSlotAvailableUseCaseImpl;
import ar.edu.turnos.turnos.availability.domain.model.Availability;
import ar.edu.turnos.turnos.availability.domain.model.AvailabilitySourceException;
import ar.edu.turnos.turnos.availability.domain.model.ProfessionalNotFoundException;
import ar.edu.turnos.turnos.availability.domain.ports.in.IsSlotAvailableUseCase;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class IsSlotAvailableUseCaseTests {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 9);
    private static final LocalTime SLOT = LocalTime.of(9, 30);

    @Test
    void checksTheExactSlotAndRejectsUnknownTimes() {
        var checker = new IsSlotAvailableUseCaseImpl((id, date) ->
                new Availability(id, date, List.of(SLOT)));

        assertThat(checker.check(15, DATE, SLOT)).isEqualTo(IsSlotAvailableUseCase.Result.AVAILABLE);
        assertThat(checker.check(15, DATE, LocalTime.of(9, 45)))
                .isEqualTo(IsSlotAvailableUseCase.Result.OCCUPIED);
        assertThat(checker.check(15, DATE, null)).isEqualTo(IsSlotAvailableUseCase.Result.INVALID_REQUEST);
    }

    @Test
    void distinguishesMissingProfessionalsFromInvalidOrUnavailableSources() {
        var notFound = new IsSlotAvailableUseCaseImpl((id, date) -> {
            throw new ProfessionalNotFoundException();
        });
        var unavailable = new IsSlotAvailableUseCaseImpl((id, date) -> {
            throw new AvailabilitySourceException(false);
        });
        var invalid = new IsSlotAvailableUseCaseImpl((id, date) -> {
            throw new AvailabilitySourceException(true);
        });
        var invalidDate = new IsSlotAvailableUseCaseImpl((id, date) -> {
            throw new IllegalArgumentException("Fecha inválida");
        });

        assertThat(notFound.check(15, DATE, SLOT)).isEqualTo(IsSlotAvailableUseCase.Result.NOT_FOUND);
        assertThat(unavailable.check(15, DATE, SLOT)).isEqualTo(IsSlotAvailableUseCase.Result.SOURCE_UNAVAILABLE);
        assertThat(invalid.check(15, DATE, SLOT)).isEqualTo(IsSlotAvailableUseCase.Result.INVALID_SOURCE_DATA);
        assertThat(invalidDate.check(15, DATE, SLOT)).isEqualTo(IsSlotAvailableUseCase.Result.INVALID_REQUEST);
    }
}

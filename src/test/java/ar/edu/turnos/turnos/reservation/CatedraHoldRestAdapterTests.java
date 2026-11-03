package ar.edu.turnos.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.turnos.turnos.reservation.application.exception.HoldCreationException;
import ar.edu.turnos.turnos.reservation.infrastructure.rest.CatedraHoldRestAdapter;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class CatedraHoldRestAdapterTests {
    private static final LocalDate DATE = LocalDate.of(2026, 11, 9);
    private static final LocalTime SLOT = LocalTime.of(9, 30);

    @Test
    void missingTechnicalJwtIsConfigurationFailureWithoutAnExternalPost() {
        var gateway = new CatedraHoldRestAdapter(RestClient.builder(), "http://127.0.0.1:1", " ", Clock.systemUTC());

        assertThatThrownBy(() -> gateway.create(15, DATE, SLOT, "user-42"))
                .isInstanceOfSatisfying(HoldCreationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(HoldCreationException.Reason.INTEGRATION_UNAVAILABLE));
    }

    @Test
    void lostConnectionAfterStartingPostHasUnknownOutcome() {
        var gateway = new CatedraHoldRestAdapter(RestClient.builder(), "http://127.0.0.1:1",
                "test-only-technical-jwt", Clock.systemUTC());

        assertThatThrownBy(() -> gateway.create(15, DATE, SLOT, "user-42"))
                .isInstanceOfSatisfying(HoldCreationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(HoldCreationException.Reason.OUTCOME_UNKNOWN));
    }
}

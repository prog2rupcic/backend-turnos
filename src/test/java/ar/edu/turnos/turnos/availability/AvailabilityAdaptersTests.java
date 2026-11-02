package ar.edu.turnos.turnos.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.turnos.turnos.availability.domain.model.AvailabilitySourceException;
import ar.edu.turnos.turnos.availability.infrastructure.rest.CatedraOccupancyRestAdapter;
import ar.edu.turnos.turnos.availability.infrastructure.rest.CatalogAgendaRestAdapter;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

class AvailabilityAdaptersTests {
    private static final String UNREACHABLE = "http://127.0.0.1:1";
    private static final LocalDate DATE = LocalDate.of(2026, 11, 9);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void catalogRequiresAnAuthenticatedJwtUser() {
        var adapter = new CatalogAgendaRestAdapter(RestClient.builder(), UNREACHABLE);

        assertThatThrownBy(() -> adapter.get(15))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void centralSourceWithoutTechnicalJwtIsUnavailableNotInvalid() {
        var adapter = new CatedraOccupancyRestAdapter(RestClient.builder(), UNREACHABLE, " ");

        assertThatThrownBy(() -> adapter.find(15, DATE))
                .isInstanceOfSatisfying(AvailabilitySourceException.class,
                        exception -> assertThat(exception.invalidResponse()).isFalse());
    }

    @Test
    void unreachableCatalogIsReportedAsUnavailable() {
        authenticate();
        var adapter = new CatalogAgendaRestAdapter(RestClient.builder(), UNREACHABLE);

        assertThatThrownBy(() -> adapter.get(15))
                .isInstanceOfSatisfying(AvailabilitySourceException.class,
                        exception -> assertThat(exception.invalidResponse()).isFalse());
    }

    @Test
    void unreachableCentralSourceIsReportedAsUnavailable() {
        var adapter = new CatedraOccupancyRestAdapter(RestClient.builder(), UNREACHABLE, "technical-jwt");

        assertThatThrownBy(() -> adapter.find(15, DATE))
                .isInstanceOfSatisfying(AvailabilitySourceException.class,
                        exception -> assertThat(exception.invalidResponse()).isFalse());
    }

    private static void authenticate() {
        var jwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", "user-15").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}

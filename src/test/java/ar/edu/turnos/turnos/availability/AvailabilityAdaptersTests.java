package ar.edu.turnos.turnos.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.turnos.turnos.availability.domain.model.AvailabilitySourceException;
import ar.edu.turnos.turnos.availability.infrastructure.rest.CatedraOccupancyRestAdapter;
import ar.edu.turnos.turnos.availability.infrastructure.rest.CatalogAgendaRestAdapter;
import ar.edu.turnos.turnos.availability.application.usecases.GetOccupanciesUseCaseImpl;
import ar.edu.turnos.turnos.reservation.infrastructure.availability.SlotOccupancyAdapter;
import ar.edu.turnos.turnos.reservation.domain.model.SlotOccupancy;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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

    @Test
    void recoveryCanObserveTheActualExpiryOfAHeldOccupancy() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/appointment-occupancies", exchange -> {
            byte[] body = """
                    {"professionalId":15,"from":"2026-11-09","to":"2026-11-09",
                     "occupancies":[{"date":"2026-11-09","startTime":"09:30",
                     "status":"HELD","expiresAt":"2026-11-09T14:00:00Z"}]}
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var source = new CatedraOccupancyRestAdapter(RestClient.builder(),
                    "http://127.0.0.1:" + server.getAddress().getPort(), "test-technical-jwt");
            var recovery = new SlotOccupancyAdapter(new GetOccupanciesUseCaseImpl(source));
            var result = recovery.get(15, DATE, LocalTime.of(9, 30));
            assertThat(result.status()).isEqualTo(SlotOccupancy.Status.HELD);
            assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-11-09T14:00:00Z"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void anInvalidOccupancyListingIsReportedAsInvalid() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/appointment-occupancies", exchange -> {
            byte[] body = """
                    {"professionalId":99,"from":"2026-11-09","to":"2026-11-09","occupancies":[]}
                    """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var source = new CatedraOccupancyRestAdapter(RestClient.builder(),
                    "http://127.0.0.1:" + server.getAddress().getPort(), "test-technical-jwt");

            assertThatThrownBy(() -> source.find(15, DATE))
                    .isInstanceOfSatisfying(AvailabilitySourceException.class,
                            exception -> assertThat(exception.invalidResponse()).isTrue());
        } finally {
            server.stop(0);
        }
    }

    private static void authenticate() {
        var jwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", "user-15").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}

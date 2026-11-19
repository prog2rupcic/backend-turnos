package ar.edu.turnos.turnos.availability.infrastructure.rest;

import ar.edu.turnos.turnos.availability.domain.model.AvailabilitySourceException;
import ar.edu.turnos.turnos.availability.domain.model.ProfessionalAgenda;
import ar.edu.turnos.turnos.availability.domain.model.ProfessionalNotFoundException;
import ar.edu.turnos.turnos.availability.domain.ports.out.ProfessionalAgendaSource;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

@Component
public class CatalogAgendaRestAdapter implements ProfessionalAgendaSource {
    private final RestClient client;

    public CatalogAgendaRestAdapter(RestClient.Builder builder,
                                    @Value("${turnos.catalog.base-url}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public ProfessionalAgenda get(long professionalId) {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication)) {
            throw new AuthenticationCredentialsNotFoundException("Autenticación requerida");
        }
        try {
            AgendaResponse response = client.get().uri("/api/professionals/{id}", professionalId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + authentication.getToken().getTokenValue())
                    .retrieve().body(AgendaResponse.class);
            if (response == null || response.id() == null || response.category() == null
                    || response.enabled() == null || response.category().enabled() == null
                    || response.weeklySchedules() == null) {
                throw new AvailabilitySourceException(true);
            }
            try {
                return new ProfessionalAgenda(response.id(), response.enabled(), response.category().enabled(),
                        response.weeklySchedules().stream().map(schedule -> new ProfessionalAgenda.WeeklySchedule(
                                schedule.dayOfWeek(), schedule.startTime(), schedule.endTime(),
                                schedule.slotDurationMinutes())).toList());
            } catch (RuntimeException exception) {
                throw new AvailabilitySourceException(true);
            }
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ProfessionalNotFoundException();
            }
            throw new AvailabilitySourceException(exception.getStatusCode().is4xxClientError()
                    && exception.getStatusCode().value() != 401 && exception.getStatusCode().value() != 403);
        } catch (RestClientException exception) {
            throw new AvailabilitySourceException(!(exception instanceof ResourceAccessException));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AgendaResponse(Long id, Boolean enabled, CategoryResponse category,
                          List<ScheduleResponse> weeklySchedules) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CategoryResponse(Boolean enabled) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ScheduleResponse(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
                            int slotDurationMinutes) {
    }
}

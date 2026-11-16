package ar.edu.turnos.turnos.availability.infrastructure.rest;

import ar.edu.turnos.turnos.availability.domain.model.AvailabilitySourceException;
import ar.edu.turnos.turnos.availability.domain.model.Occupancy;
import ar.edu.turnos.turnos.availability.domain.ports.out.OccupancySource;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

@Component
public class CatedraOccupancyRestAdapter implements OccupancySource {
    private final RestClient client;
    private final String technicalJwt;

    public CatedraOccupancyRestAdapter(RestClient.Builder builder,
                                      @Value("${turnos.catedra.base-url}") String baseUrl,
                                      @Value("${turnos.catedra.technical-jwt}") String technicalJwt) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
        this.technicalJwt = technicalJwt;
    }

    @Override
    public List<Occupancy> find(long professionalId, LocalDate date) {
        if (technicalJwt.isBlank()) {
            throw new AvailabilitySourceException(false);
        }
        try {
            OccupancyResponse response = client.get().uri(uri -> uri.path("/api/appointment-occupancies")
                            .queryParam("professionalId", professionalId).queryParam("from", date)
                            .queryParam("to", date).build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + technicalJwt)
                    .retrieve().body(OccupancyResponse.class);
            if (response == null || response.professionalId() == null || response.professionalId() != professionalId
                    || !date.equals(response.from()) || !date.equals(response.to()) || response.occupancies() == null) {
                throw new AvailabilitySourceException(true);
            }
            try {
                return response.occupancies().stream().map(occupancy -> new Occupancy(occupancy.date(),
                        occupancy.startTime(), occupancy.status(), occupancy.expiresAt())).toList();
            } catch (RuntimeException exception) {
                throw new AvailabilitySourceException(true);
            }
        } catch (RestClientResponseException exception) {
            throw new AvailabilitySourceException(exception.getStatusCode().is4xxClientError()
                    && exception.getStatusCode().value() != 401 && exception.getStatusCode().value() != 403);
        } catch (RestClientException exception) {
            throw new AvailabilitySourceException(!(exception instanceof ResourceAccessException));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OccupancyResponse(Long professionalId, LocalDate from, LocalDate to,
                             List<OccupancyItem> occupancies) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OccupancyItem(LocalDate date, LocalTime startTime, Occupancy.Status status, java.time.Instant expiresAt) {
    }
}

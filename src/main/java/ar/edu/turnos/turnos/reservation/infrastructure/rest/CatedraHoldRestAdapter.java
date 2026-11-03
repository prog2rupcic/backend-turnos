package ar.edu.turnos.turnos.reservation.infrastructure.rest;

import ar.edu.turnos.turnos.reservation.application.exception.HoldCreationException;
import ar.edu.turnos.turnos.reservation.domain.model.CreatedHold;
import ar.edu.turnos.turnos.reservation.domain.ports.out.HoldGateway;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CatedraHoldRestAdapter implements HoldGateway {
    private static final JsonMapper ERRORS = JsonMapper.builder().build();
    private static final DateTimeFormatter HOUR_MINUTE = DateTimeFormatter.ofPattern("HH:mm");
    private final RestClient client;
    private final String technicalJwt;
    private final Clock clock;

    public CatedraHoldRestAdapter(RestClient.Builder builder,
                                  @Value("${turnos.catedra.base-url}") String baseUrl,
                                  @Value("${turnos.catedra.technical-jwt}") String technicalJwt, Clock clock) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
        this.technicalJwt = technicalJwt;
        this.clock = clock;
    }

    @Override
    public CreatedHold create(long professionalId, LocalDate date, LocalTime startTime, String externalPatientId) {
        if (technicalJwt.isBlank()) {
            throw new HoldCreationException(HoldCreationException.Reason.INTEGRATION_UNAVAILABLE);
        }
        try {
            var result = client.post().uri("/api/appointment-holds")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + technicalJwt)
                    .body(new HoldRequest(professionalId, date, startTime.format(HOUR_MINUTE), externalPatientId))
                    .retrieve().toEntity(HoldResponse.class);
            HoldResponse response = result.getBody();
            if (result.getStatusCode() != HttpStatus.CREATED || response == null
                    || response.holdId() == null || response.holdId().isBlank()
                    || response.reservationProcessId() == null || response.reservationProcessId().isBlank()
                    || !"HELD".equals(response.status()) || response.expiresAt() == null
                    || !response.expiresAt().isAfter(clock.instant())) {
                throw new HoldCreationException(HoldCreationException.Reason.OUTCOME_UNKNOWN);
            }
            return new CreatedHold(response.holdId(), response.reservationProcessId(), response.expiresAt());
        } catch (RestClientResponseException exception) {
            throw translate(exception);
        } catch (RestClientException exception) {
            // Tras enviar un POST, un timeout o una respuesta ilegible no prueba que no se creó el hold.
            throw new HoldCreationException(HoldCreationException.Reason.OUTCOME_UNKNOWN);
        }
    }

    private static HoldCreationException translate(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new HoldCreationException(HoldCreationException.Reason.INTEGRATION_UNAVAILABLE);
        }
        if (status >= 500) {
            return new HoldCreationException(HoldCreationException.Reason.OUTCOME_UNKNOWN);
        }
        String code = errorCode(exception);
        if (status == 404 && "PROFESSIONAL_NOT_FOUND".equals(code)) {
            return new HoldCreationException(HoldCreationException.Reason.PROFESSIONAL_NOT_FOUND);
        }
        if ((status == 409 && ("SLOT_ALREADY_HELD".equals(code) || "SLOT_ALREADY_RESERVED".equals(code)))
                || (status == 400 && ("INVALID_SLOT".equals(code) || "PROFESSIONAL_DISABLED".equals(code)))) {
            return new HoldCreationException(HoldCreationException.Reason.SLOT_UNAVAILABLE);
        }
        return new HoldCreationException(HoldCreationException.Reason.INVALID_RESPONSE);
    }

    private static String errorCode(RestClientResponseException exception) {
        try {
            var json = ERRORS.readTree(exception.getResponseBodyAsByteArray());
            return json.path("code").asText();
        } catch (Exception malformed) {
            return "";
        }
    }

    record HoldRequest(long professionalId, LocalDate date, String startTime, String externalPatientId) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HoldResponse(String holdId, String reservationProcessId, String status, Instant expiresAt) { }
}

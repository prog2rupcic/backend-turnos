package ar.edu.turnos.turnos.reservation.infrastructure.rest;

import ar.edu.turnos.turnos.reservation.domain.model.CancellationReceipt;
import ar.edu.turnos.turnos.reservation.domain.model.OwnReservation;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationOperationException;
import ar.edu.turnos.turnos.reservation.domain.ports.out.CourseReservationsGateway;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
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
public class CatedraReservationsRestAdapter implements CourseReservationsGateway {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> STATUSES = Set.of("PHONE_PENDING", "CONFIRMED", "CANCELLED", "FAILED");
    private final RestClient client;
    private final String technicalJwt;

    public CatedraReservationsRestAdapter(RestClient.Builder builder,
                                          @Value("${turnos.catedra.base-url}") String baseUrl,
                                          @Value("${turnos.catedra.technical-jwt}") String technicalJwt) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
        this.technicalJwt = technicalJwt;
    }

    @Override
    public Page list(String externalPatientId, int page, int size) {
        requireToken();
        try {
            var result = client.get().uri(uri -> uri.path("/api/appointments")
                            .queryParam("externalPatientId", externalPatientId)
                            .queryParam("page", page).queryParam("size", size).build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + technicalJwt)
                    .retrieve().toEntity(String.class);
            if (result.getStatusCode() != HttpStatus.OK || result.getBody() == null) { throw invalid(); }
            var count = result.getHeaders().getFirst("X-Total-Count");
            long total = Long.parseLong(count);
            if (total < 0 || total > 10_000) { throw invalid(); }
            Appointment[] appointments = JSON.readValue(result.getBody(), Appointment[].class);
            if (appointments == null || appointments.length > size) { throw invalid(); }
            List<OwnReservation> items = Arrays.stream(appointments)
                    .filter(item -> externalPatientId.equals(item.externalPatientId()))
                    .map(CatedraReservationsRestAdapter::toDomain).toList();
            return new Page(items, total, appointments.length);
        } catch (ReservationOperationException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw responseError(exception, false);
        } catch (RestClientException _) {
            throw new ReservationOperationException(ReservationOperationException.Reason.INTEGRATION_UNAVAILABLE);
        } catch (RuntimeException _) {
            throw invalid();
        }
    }

    @Override
    public CancellationReceipt cancel(String reservationProcessId, String reason) {
        requireToken();
        try {
            var result = client.post().uri("/api/appointments/{processId}/cancel", reservationProcessId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + technicalJwt)
                    .body(new CancelRequest(reason)).retrieve().toEntity(CancelResponse.class);
            var body = result.getBody();
            if (result.getStatusCode() != HttpStatus.OK || body == null || body.reservationId() == null
                    || body.reservationId() <= 0 || !reservationProcessId.equals(body.reservationProcessId())
                    || !"CANCELLED".equals(body.status()) || body.cancelledAt() == null) {
                throw new ReservationOperationException(ReservationOperationException.Reason.OUTCOME_UNKNOWN);
            }
            return new CancellationReceipt(body.reservationId(), body.reservationProcessId(),
                    Instant.parse(body.cancelledAt()));
        } catch (ReservationOperationException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            throw responseError(exception, true);
        } catch (RuntimeException _) {
            throw new ReservationOperationException(ReservationOperationException.Reason.OUTCOME_UNKNOWN);
        }
    }

    private void requireToken() {
        if (technicalJwt == null || technicalJwt.isBlank()) {
            throw new ReservationOperationException(ReservationOperationException.Reason.INTEGRATION_UNAVAILABLE);
        }
    }

    private static OwnReservation toDomain(Appointment item) {
        if (item.reservationId() == null || item.reservationId() <= 0 || blank(item.reservationProcessId())
                || item.professionalId() == null || item.professionalId() <= 0 || blank(item.appointmentDate())
                || blank(item.startTime()) || blank(item.endTime()) || !STATUSES.contains(item.status())) {
            throw invalid();
        }
        return new OwnReservation(item.reservationProcessId(), item.reservationId(), item.professionalId(),
                LocalDate.parse(item.appointmentDate()), LocalTime.parse(item.startTime()), LocalTime.parse(item.endTime()),
                item.professionalFirstName(), item.professionalLastName(), item.status(),
                instant(item.confirmedAt()), instant(item.cancelledAt()), item.cancellationReason());
    }

    private static Instant instant(String value) { return value == null ? null : Instant.parse(value); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static ReservationOperationException invalid() {
        return new ReservationOperationException(ReservationOperationException.Reason.INVALID_RESPONSE);
    }

    private static ReservationOperationException responseError(RestClientResponseException response, boolean effect) {
        int status = response.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new ReservationOperationException(ReservationOperationException.Reason.INTEGRATION_UNAVAILABLE);
        }
        return new ReservationOperationException(effect ? ReservationOperationException.Reason.OUTCOME_UNKNOWN
                : ReservationOperationException.Reason.INTEGRATION_UNAVAILABLE);
    }

    record CancelRequest(String reason) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CancelResponse(Long reservationId, String reservationProcessId, String status, String cancelledAt) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Appointment(Long reservationId, String reservationProcessId, String externalPatientId,
                       String appointmentDate, String startTime, String endTime, Long professionalId,
                       String professionalFirstName, String professionalLastName, String status,
                       String confirmedAt, String cancelledAt, String cancellationReason) { }
}

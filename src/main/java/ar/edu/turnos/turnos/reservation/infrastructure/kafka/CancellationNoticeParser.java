package ar.edu.turnos.turnos.reservation.infrastructure.kafka;

import ar.edu.turnos.turnos.reservation.domain.model.CancellationNotice;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CancellationNoticeParser {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final String groupId;

    public CancellationNoticeParser(@Value("${turnos.integration.group-id:}") String groupId) {
        this.groupId = groupId;
    }

    public CancellationNotice parse(String payload, String key) {
        var value = JSON.readValue(payload, Payload.class);
        if (value == null || !"AppointmentCancelled".equals(value.eventType())
                || value.schemaVersion() == null || value.schemaVersion() != 1
                || groupId.isBlank() || !groupId.equals(value.groupId())
                || blank(value.eventId()) || value.eventId().length() > 100
                || blank(value.reservationProcessId()) || value.reservationProcessId().length() > 100
                || !value.reservationProcessId().equals(key)
                || blank(value.holdId()) || blank(value.requestEventId())
                || value.reservationId() == null || value.reservationId() <= 0
                || value.professionalId() == null || value.professionalId() <= 0
                || !"CANCELLED".equals(value.status())
                || value.reason() != null && value.reason().length() > 500) {
            throw new IllegalArgumentException("Cancelación Kafka inválida");
        }
        return new CancellationNotice(value.eventId(), value.reservationProcessId(), value.holdId(),
                value.requestEventId(), value.reservationId(), value.professionalId(),
                LocalDate.parse(value.date()), LocalTime.parse(value.startTime()), value.reason(),
                Instant.parse(value.occurredAt()));
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(String eventId, String eventType, Integer schemaVersion, String groupId,
                   String reservationProcessId, String holdId, String requestEventId, Long reservationId,
                   Long professionalId, String date, String startTime, String status, String reason,
                   String occurredAt) { }
}

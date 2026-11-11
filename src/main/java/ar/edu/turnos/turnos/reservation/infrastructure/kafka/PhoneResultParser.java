package ar.edu.turnos.turnos.reservation.infrastructure.kafka;

import ar.edu.turnos.turnos.reservation.domain.model.PhoneResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PhoneResultParser {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final String groupId;

    public PhoneResultParser(@Value("${turnos.integration.group-id:}") String groupId) { this.groupId = groupId; }

    public String eventType(String payload) {
        return JSON.readTree(payload).path("eventType").asText();
    }

    public PhoneResult parse(String payload, String key) {
        var value = JSON.readValue(payload, Payload.class);
        if (value == null || value.schemaVersion() == null || value.schemaVersion() != 1
                || groupId.isBlank() || !groupId.equals(value.groupId())
                || !validId(value.eventId()) || !validId(value.reservationProcessId())
                || !validId(value.holdId()) || !validId(value.requestEventId())
                || !value.reservationProcessId().equals(key)) {
            throw new IllegalArgumentException("Evento de resultado inválido");
        }
        Instant occurredAt = Instant.parse(value.occurredAt());
        if ("AppointmentConfirmed".equals(value.eventType())) {
            if (!"CONFIRMED".equals(value.status()) || value.reservationId() == null || value.reservationId() <= 0
                    || value.professionalId() == null || value.professionalId() <= 0) {
                throw new IllegalArgumentException("Confirmación inválida");
            }
            return new PhoneResult(value.eventId(), value.groupId(), value.reservationProcessId(), value.holdId(),
                    value.requestEventId(), value.reservationId(), value.professionalId(), LocalDate.parse(value.date()),
                    LocalTime.parse(value.startTime()), null, null, null, occurredAt, PhoneResult.Type.CONFIRMED);
        }
        if (!validId(value.reason())) {
            throw new IllegalArgumentException("Motivo del evento inválido");
        }
        if ("AdditionalInformationRejected".equals(value.eventType())) {
            if (value.message() == null || value.message().length() > 2000 || !validId(value.expiresAt())) {
                throw new IllegalArgumentException("Rechazo inválido");
            }
            return new PhoneResult(value.eventId(), value.groupId(), value.reservationProcessId(), value.holdId(),
                    value.requestEventId(), null, null, null, null, value.reason(), value.message(),
                    Instant.parse(value.expiresAt()), occurredAt, PhoneResult.Type.REJECTED);
        }
        if ("AppointmentProcessExpired".equals(value.eventType())) {
            return terminal(value, occurredAt, PhoneResult.Type.EXPIRED);
        }
        if ("AppointmentProcessInvalid".equals(value.eventType())) {
            return terminal(value, occurredAt, PhoneResult.Type.INVALID);
        }
        throw new IllegalArgumentException("Tipo de evento de proceso no soportado");
    }

    private static PhoneResult terminal(Payload value, Instant occurredAt, PhoneResult.Type type) {
        return new PhoneResult(value.eventId(), value.groupId(), value.reservationProcessId(), value.holdId(),
                value.requestEventId(), null, null, null, null, value.reason(), null, null, occurredAt, type);
    }

    private static boolean validId(String value) { return value != null && !value.isBlank() && value.length() <= 100; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(String eventId, String eventType, Integer schemaVersion, String groupId,
                   String reservationProcessId, String holdId, String requestEventId, Long reservationId,
                   Long professionalId, String date, String startTime, String status, String reason,
                   String message, String expiresAt, String occurredAt) { }
}

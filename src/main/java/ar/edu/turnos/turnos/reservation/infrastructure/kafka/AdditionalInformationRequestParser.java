package ar.edu.turnos.turnos.reservation.infrastructure.kafka;

import ar.edu.turnos.turnos.reservation.domain.model.AdditionalInformationRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AdditionalInformationRequestParser {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String EVENT_TYPE = "AdditionalInformationRequested";
    private static final int SCHEMA_VERSION = 1;
    private static final String PHONE_NUMBER_FIELD = "PHONE_NUMBER";
    private static final int MAX_MESSAGE_LENGTH = 2000;

    public AdditionalInformationRequest parse(String payload, String messageKey) {
        Payload parsed = read(payload);
        if (!EVENT_TYPE.equals(parsed.eventType())) {
            throw invalid(InvalidAdditionalInformationRequestException.Reason.UNEXPECTED_EVENT_TYPE);
        }
        if (parsed.schemaVersion() == null || parsed.schemaVersion() != SCHEMA_VERSION) {
            throw invalid(InvalidAdditionalInformationRequestException.Reason.UNSUPPORTED_SCHEMA_VERSION);
        }
        if (blank(parsed.eventId()) || blank(parsed.reservationProcessId()) || blank(parsed.holdId())
                || blank(parsed.expiresAt())) {
            throw invalid(InvalidAdditionalInformationRequestException.Reason.MISSING_FIELD);
        }
        if (!PHONE_NUMBER_FIELD.equals(parsed.requiredField())) {
            throw invalid(InvalidAdditionalInformationRequestException.Reason.UNSUPPORTED_REQUIRED_FIELD);
        }
        if (parsed.message() != null && parsed.message().length() > MAX_MESSAGE_LENGTH) {
            throw invalid(InvalidAdditionalInformationRequestException.Reason.INVALID_FIELD);
        }
        if (!parsed.reservationProcessId().equals(messageKey)) {
            throw invalid(InvalidAdditionalInformationRequestException.Reason.KEY_MISMATCH);
        }
        return new AdditionalInformationRequest(parsed.eventId(), parsed.reservationProcessId(), parsed.holdId(),
                parsed.requiredField(), parsed.message(), instant(parsed.expiresAt()), instant(parsed.occurredAt()));
    }

    private static Payload read(String payload) {
        if (blank(payload)) {
            throw invalid(InvalidAdditionalInformationRequestException.Reason.MALFORMED);
        }
        try {
            Payload parsed = JSON.readValue(payload, Payload.class);
            if (parsed == null) {
                throw invalid(InvalidAdditionalInformationRequestException.Reason.MALFORMED);
            }
            return parsed;
        } catch (JacksonException _) {
            throw invalid(InvalidAdditionalInformationRequestException.Reason.MALFORMED);
        }
    }

    private static Instant instant(String value) {
        if (blank(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException _) {
            throw invalid(InvalidAdditionalInformationRequestException.Reason.INVALID_FIELD);
        }
    }

    private static InvalidAdditionalInformationRequestException invalid(
            InvalidAdditionalInformationRequestException.Reason reason) {
        return new InvalidAdditionalInformationRequestException(reason);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(String eventId, String eventType, Integer schemaVersion, String reservationProcessId,
                   String holdId, String requiredField, String message, String expiresAt, String occurredAt) {
    }
}

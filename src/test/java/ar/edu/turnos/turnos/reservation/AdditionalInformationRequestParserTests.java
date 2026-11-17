package ar.edu.turnos.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.turnos.turnos.reservation.domain.model.AdditionalInformationRequest;
import ar.edu.turnos.turnos.reservation.infrastructure.kafka.AdditionalInformationRequestParser;
import ar.edu.turnos.turnos.reservation.infrastructure.kafka.InvalidAdditionalInformationRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AdditionalInformationRequestParserTests {
    private final AdditionalInformationRequestParser parser = new AdditionalInformationRequestParser();

    @Test
    void deserializesTheContractExampleAndToleratesUnknownFields() throws IOException {
        AdditionalInformationRequest request = parser.parse(contractExample(), "process-abc");

        assertThat(request.eventId()).isEqualTo("evt-7f2a");
        assertThat(request.reservationProcessId()).isEqualTo("process-abc");
        assertThat(request.holdId()).isEqualTo("hold-xyz");
        assertThat(request.requiredField()).isEqualTo("PHONE_NUMBER");
        assertThat(request.message()).isEqualTo("Ingrese un teléfono de contacto");
        assertThat(request.expiresAt()).isEqualTo(Instant.parse("2026-11-03T17:10:00Z"));
        assertThat(request.occurredAt()).isEqualTo(Instant.parse("2026-11-03T17:00:05Z"));
    }

    @Test
    void acceptsAnOffsetInstantAndAMissingOptionalOccurredAt() {
        var request = parser.parse("""
                {"eventId":"e1","eventType":"AdditionalInformationRequested","schemaVersion":1,
                 "reservationProcessId":"p1","holdId":"h1","requiredField":"PHONE_NUMBER",
                 "expiresAt":"2026-11-03T14:10:00-03:00"}
                """, "p1");

        assertThat(request.expiresAt()).isEqualTo(Instant.parse("2026-11-03T17:10:00Z"));
        assertThat(request.occurredAt()).isNull();
    }

    @Test
    void rejectsContractViolations() {
        assertReason("", "p1", InvalidAdditionalInformationRequestException.Reason.MALFORMED);
        assertReason("{", "p1", InvalidAdditionalInformationRequestException.Reason.MALFORMED);
        assertReason(valid().replace("AdditionalInformationRequested", "OtherEvent"), "p1",
                InvalidAdditionalInformationRequestException.Reason.UNEXPECTED_EVENT_TYPE);
        assertReason(valid().replace("\"schemaVersion\":1", "\"schemaVersion\":2"), "p1",
                InvalidAdditionalInformationRequestException.Reason.UNSUPPORTED_SCHEMA_VERSION);
        assertReason(valid().replace("\"schemaVersion\":1", "\"schemaVersion\":null"), "p1",
                InvalidAdditionalInformationRequestException.Reason.UNSUPPORTED_SCHEMA_VERSION);
        assertReason(valid().replace("\"holdId\":\"h1\",", ""), "p1",
                InvalidAdditionalInformationRequestException.Reason.MISSING_FIELD);
        assertReason(valid().replace("PHONE_NUMBER", "EMAIL"), "p1",
                InvalidAdditionalInformationRequestException.Reason.UNSUPPORTED_REQUIRED_FIELD);
        assertReason(valid().replace("2026-11-03T17:10:00Z", "not-an-instant"), "p1",
                InvalidAdditionalInformationRequestException.Reason.INVALID_FIELD);
        assertReason(valid(), "other-key",
                InvalidAdditionalInformationRequestException.Reason.KEY_MISMATCH);
    }

    private static void assertReason(String payload, String key,
                                      InvalidAdditionalInformationRequestException.Reason expected) {
        assertThatThrownBy(() -> new AdditionalInformationRequestParser().parse(payload, key))
                .isInstanceOfSatisfying(InvalidAdditionalInformationRequestException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(expected));
    }

    private static String valid() {
        return """
                {"eventId":"e1","eventType":"AdditionalInformationRequested","schemaVersion":1,
                 "reservationProcessId":"p1","holdId":"h1","requiredField":"PHONE_NUMBER",
                 "message":"Ingrese un teléfono","expiresAt":"2026-11-03T17:10:00Z",
                 "occurredAt":"2026-11-03T17:00:05Z"}
                """;
    }

    private static String contractExample() throws IOException {
        try (InputStream stream = AdditionalInformationRequestParserTests.class
                .getResourceAsStream("/contracts/additional-information-requested.json")) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

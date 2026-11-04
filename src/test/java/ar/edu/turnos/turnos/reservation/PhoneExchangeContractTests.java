package ar.edu.turnos.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.turnos.turnos.reservation.domain.model.PhoneSubmission;
import ar.edu.turnos.turnos.reservation.infrastructure.kafka.PhoneResultParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneExchangeContractTests {
    private final PhoneResultParser parser = new PhoneResultParser("proyecto-juan-perez");

    @Test
    void parsesThePublishedConfirmationAndRejectionContractsWithUnknownFields() throws IOException {
        var confirmation = parser.parse(fixture("appointment-confirmed.json"), "process-a92b47d0");
        assertThat(confirmation.confirmed()).isTrue();
        assertThat(confirmation.reservationId()).isEqualTo(8251);
        assertThat(confirmation.startTime()).isEqualTo(LocalTime.of(9, 30));
        var rejection = parser.parse(fixture("additional-information-rejected.json"), "process-a92b47d0");
        assertThat(rejection.confirmed()).isFalse();
        assertThat(rejection.reason()).isEqualTo("INVALID_PHONE_NUMBER");
        assertThat(parser.parse(fixture("appointment-confirmed.json").replace("09:30", "09:30:00"),
                "process-a92b47d0").startTime()).isEqualTo(LocalTime.of(9, 30));
    }

    @ParameterizedTest
    @ValueSource(strings = {"groupId", "requestEventId", "holdId", "eventId", "schemaVersion", "occurredAt",
            "reservationId", "professionalId", "date", "startTime", "status"})
    void rejectsMissingRequiredConfirmationFields(String field) throws IOException {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        var node = (tools.jackson.databind.node.ObjectNode) json.readTree(fixture("appointment-confirmed.json"));
        node.remove(field);
        String payload = json.writeValueAsString(node);
        assertThatThrownBy(() -> parser.parse(payload, "process-a92b47d0")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsAnotherIntegrationKeyVersionAndMalformedMessage() throws IOException {
        String payload = fixture("appointment-confirmed.json");
        assertThatThrownBy(() -> parser.parse(payload, "other-process")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PhoneResultParser("other-integration").parse(payload, "process-a92b47d0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(payload.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"),
                "process-a92b47d0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("not json", "process-a92b47d0")).isInstanceOf(RuntimeException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123456", "1234567890123456", "+abc1234567", "++1234567", "123/4567"})
    void rejectsInvalidPhonesBeforePublishing(String phone) {
        assertThatThrownBy(() -> PhoneSubmission.normalize(phone)).isInstanceOf(IllegalArgumentException.class);
    }

    private static String fixture(String name) throws IOException {
        try (var input = PhoneExchangeContractTests.class.getResourceAsStream("/contracts/" + name)) {
            if (input == null) { throw new IOException("Missing contract fixture"); }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

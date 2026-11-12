package ar.edu.turnos.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.turnos.turnos.reservation.infrastructure.kafka.CancellationNoticeParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CancellationNoticeParserTests {
    private final CancellationNoticeParser parser = new CancellationNoticeParser("proyecto-juan-perez");

    @Test
    void readsContractAndAllowsNullReasonAndAdditionalFields() throws IOException {
        var fixture = fixture();
        var notice = parser.parse(fixture, "process-a92b47d0");
        assertThat(notice.reservationId()).isEqualTo(8251L);
        assertThat(notice.reason()).isNull();
        assertThat(parser.parse(fixture.replace("09:30", "09:30:00"), "process-a92b47d0").startTime())
                .isEqualTo(notice.startTime());
    }

    @Test
    void rejectsWrongIntegrationKeyAndMissingFields() throws IOException {
        var fixture = fixture();
        assertThatThrownBy(() -> parser.parse(fixture, "another-process")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CancellationNoticeParser("another-group")
                .parse(fixture, "process-a92b47d0")).isInstanceOf(IllegalArgumentException.class);
        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        for (var field : new String[] {"eventId", "eventType", "schemaVersion", "groupId", "reservationProcessId",
                "holdId", "requestEventId", "reservationId", "professionalId", "date", "startTime", "status",
                "occurredAt"}) {
            var node = (tools.jackson.databind.node.ObjectNode) mapper.readTree(fixture);
            node.remove(field);
            String invalid = mapper.writeValueAsString(node);
            assertThatThrownBy(() -> parser.parse(invalid, "process-a92b47d0"))
                    .as("missing %s", field).isInstanceOf(RuntimeException.class);
        }
    }

    private static String fixture() throws IOException {
        try (var in = CancellationNoticeParserTests.class.getResourceAsStream("/contracts/appointment-cancelled.json")) {
            if (in == null) { throw new IOException("Missing fixture"); }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

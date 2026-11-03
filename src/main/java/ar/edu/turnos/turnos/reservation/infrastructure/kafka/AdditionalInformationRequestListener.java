package ar.edu.turnos.turnos.reservation.infrastructure.kafka;

import ar.edu.turnos.turnos.reservation.domain.model.AdditionalInformationRequest;
import ar.edu.turnos.turnos.reservation.domain.model.PhoneRequestOutcome;
import ar.edu.turnos.turnos.reservation.domain.ports.in.RecordPhoneRequestUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "turnos.kafka.enabled", havingValue = "true")
public class AdditionalInformationRequestListener {
    private static final Logger LOG = LoggerFactory.getLogger(AdditionalInformationRequestListener.class);

    private final AdditionalInformationRequestParser parser;
    private final RecordPhoneRequestUseCase useCase;

    public AdditionalInformationRequestListener(AdditionalInformationRequestParser parser,
                                                RecordPhoneRequestUseCase useCase) {
        this.parser = parser;
        this.useCase = useCase;
    }

    @KafkaListener(topics = "${turnos.kafka.actions-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(ConsumerRecord<String, String> record) {
        AdditionalInformationRequest request;
        try {
            request = parser.parse(record.value(), record.key());
        } catch (InvalidAdditionalInformationRequestException exception) {
            LOG.warn("Se descarta evento Kafka inválido topic={} partition={} offset={} reason={}",
                    record.topic(), record.partition(), record.offset(), exception.reason());
            return;
        }
        // Los fallos inesperados (por ejemplo, base de datos caída) se propagan para reintentar sin confirmar el offset.
        PhoneRequestOutcome outcome = useCase.record(request);
        LOG.info("Solicitud de teléfono procesada eventId={} reservationProcessId={} outcome={}",
                request.eventId(), request.reservationProcessId(), outcome);
    }
}

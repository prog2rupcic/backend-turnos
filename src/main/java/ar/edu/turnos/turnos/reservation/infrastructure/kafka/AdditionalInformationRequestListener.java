package ar.edu.turnos.turnos.reservation.infrastructure.kafka;

import ar.edu.turnos.turnos.reservation.domain.model.AdditionalInformationRequest;
import ar.edu.turnos.turnos.reservation.domain.model.PhoneRequestOutcome;
import ar.edu.turnos.turnos.reservation.domain.ports.in.RecordPhoneRequestUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.in.RecordPhoneResultUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.in.RecordCancellationUseCase;
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
    private final PhoneResultParser resultParser;
    private final RecordPhoneResultUseCase resultUseCase;
    private final CancellationNoticeParser cancellationParser;
    private final RecordCancellationUseCase cancellationUseCase;

    public AdditionalInformationRequestListener(AdditionalInformationRequestParser parser,
                                                 RecordPhoneRequestUseCase useCase, PhoneResultParser resultParser,
                                                 RecordPhoneResultUseCase resultUseCase,
                                                 CancellationNoticeParser cancellationParser,
                                                 RecordCancellationUseCase cancellationUseCase) {
        this.parser = parser;
        this.useCase = useCase;
        this.resultParser = resultParser;
        this.resultUseCase = resultUseCase;
        this.cancellationParser = cancellationParser;
        this.cancellationUseCase = cancellationUseCase;
    }

    @KafkaListener(topics = "${turnos.kafka.actions-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(ConsumerRecord<String, String> record) {
        String type;
        try {
            type = resultParser.eventType(record.value());
        } catch (RuntimeException _) {
            LOG.warn("Se descarta evento Kafka ilegible partition={} offset={}", record.partition(), record.offset());
            return;
        }
        if (!"AdditionalInformationRequested".equals(type)) {
            if ("AppointmentCancelled".equals(type)) {
                onCancelled(record);
                return;
            }
            onResult(record, type);
            return;
        }
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

    private void onCancelled(ConsumerRecord<String, String> record) {
        ar.edu.turnos.turnos.reservation.domain.model.CancellationNotice notice;
        try {
            notice = cancellationParser.parse(record.value(), record.key());
        } catch (RuntimeException _) {
            LOG.warn("Se descarta cancelación Kafka inválida partition={} offset={}",
                    record.partition(), record.offset());
            return;
        }
        String outcome = cancellationUseCase.record(notice);
        LOG.info("Cancelación procesada eventId={} outcome={}", notice.eventId(), outcome);
    }

    private void onResult(ConsumerRecord<String, String> record, String type) {
        if (!"AppointmentConfirmed".equals(type) && !"AdditionalInformationRejected".equals(type)
                && !"AppointmentProcessExpired".equals(type) && !"AppointmentProcessInvalid".equals(type)) {
            LOG.info("Tipo de evento todavía no soportado partition={} offset={}", record.partition(), record.offset());
            return;
        }
        ar.edu.turnos.turnos.reservation.domain.model.PhoneResult result;
        try {
            result = resultParser.parse(record.value(), record.key());
        } catch (RuntimeException _) {
            LOG.warn("Se descarta resultado Kafka inválido partition={} offset={}", record.partition(), record.offset());
            return;
        }
        String outcome = resultUseCase.record(result);
        LOG.info("Resultado de proceso procesado eventId={} type={} outcome={} reason={}", result.eventId(),
                result.type(), outcome, result.reason());
    }
}

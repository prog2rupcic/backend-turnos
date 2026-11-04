package ar.edu.turnos.turnos.reservation.infrastructure.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "turnos.kafka.enabled", havingValue = "true")
public class KafkaConsumerConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumerConfiguration.class);

    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(
                (record, exception) -> LOG.error("Se descarta evento Kafka tras reintentos topic={} partition={} "
                        + "offset={} key={}", record.topic(), record.partition(), record.offset(), record.key(),
                        exception),
                new FixedBackOff(2000L, 3L));
    }
}

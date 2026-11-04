package ar.edu.turnos.turnos.reservation.infrastructure.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.ProducerListener;

@Configuration
public class PhoneProducerConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(PhoneProducerConfiguration.class);

    @Bean
    ProducerListener<Object, Object> kafkaProducerListener() {
        return new ProducerListener<>() {
            @Override
            public void onError(ProducerRecord<Object, Object> record, RecordMetadata metadata, Exception exception) {
                LOG.warn("Publicación Kafka fallida topic={}; el envío durable queda pendiente", record.topic());
            }
        };
    }
}

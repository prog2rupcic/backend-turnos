package ar.edu.turnos.turnos.reservation.infrastructure.kafka;

import ar.edu.turnos.turnos.reservation.domain.ports.in.PublishPhoneOutboxUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "turnos.kafka.enabled", havingValue = "true")
public class PhoneOutboxJob {
    private static final Logger LOG = LoggerFactory.getLogger(PhoneOutboxJob.class);
    private final PublishPhoneOutboxUseCase useCase;

    public PhoneOutboxJob(PublishPhoneOutboxUseCase useCase) { this.useCase = useCase; }

    @Scheduled(fixedDelayString = "${turnos.outbox.interval:2000}")
    public void publish() {
        try {
            useCase.publishPending();
        } catch (RuntimeException _) {
            LOG.warn("No se pudo completar el ciclo de outbox; los envíos persistidos se recuperarán");
        }
    }
}

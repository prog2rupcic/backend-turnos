package ar.edu.turnos.turnos.reservation.application.usecases;

import ar.edu.turnos.turnos.reservation.domain.ports.in.PublishPhoneOutboxUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.out.PhoneEventPublisher;
import ar.edu.turnos.turnos.reservation.domain.ports.out.PhoneExchangeRepository;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class PublishPhoneOutboxUseCaseImpl implements PublishPhoneOutboxUseCase {
    private final PhoneExchangeRepository exchange;
    private final PhoneEventPublisher publisher;
    private final Clock clock;

    public PublishPhoneOutboxUseCaseImpl(PhoneExchangeRepository exchange, PhoneEventPublisher publisher, Clock clock) {
        this.exchange = exchange;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    public void publishPending() {
        // Claims are committed before Kafka I/O; a lease recovers a crash before acknowledgement.
        for (int count = 0; count < 10; count++) {
            var found = exchange.claim(clock.instant());
            if (found.isEmpty()) { return; }
            var submission = found.get();
            try {
                publisher.publish(submission);
            } catch (RuntimeException _) {
                exchange.failed(submission, clock.instant());
                continue;
            }
            // If this write fails, keep the lease: resend the same event after recovery.
            exchange.published(submission, clock.instant());
        }
    }
}

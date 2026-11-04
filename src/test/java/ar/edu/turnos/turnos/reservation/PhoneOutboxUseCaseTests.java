package ar.edu.turnos.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import ar.edu.turnos.turnos.reservation.application.usecases.PublishPhoneOutboxUseCaseImpl;
import ar.edu.turnos.turnos.reservation.domain.model.PhoneSubmission;
import ar.edu.turnos.turnos.reservation.domain.ports.out.PhoneEventPublisher;
import ar.edu.turnos.turnos.reservation.domain.ports.out.PhoneExchangeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PhoneOutboxUseCaseTests {
    private final Instant now = Instant.parse("2026-11-04T12:00:00Z");
    private final PhoneExchangeRepository exchange = mock(PhoneExchangeRepository.class);
    private final PhoneEventPublisher publisher = mock(PhoneEventPublisher.class);
    private final PhoneSubmission message = new PhoneSubmission("event-1", "local-1", "integration", "process-1",
            "hold-1", "request-1", "1234567", now, now.plusSeconds(600), PhoneSubmission.DeliveryStatus.IN_FLIGHT, 1);
    private final PublishPhoneOutboxUseCaseImpl useCase = new PublishPhoneOutboxUseCaseImpl(exchange, publisher,
            Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void unknownPublishOutcomeIsRecordedAsRetryableInsteadOfDelivered() {
        when(exchange.claim(now)).thenReturn(Optional.of(message), Optional.empty());
        doThrow(new IllegalStateException("broker timeout")).when(publisher).publish(message);
        useCase.publishPending();
        verify(exchange).failed(message, now);
        verify(exchange, never()).published(any(), any());
    }

    @Test
    void databaseFailureAfterPublishLeavesTheLeaseForAnIdenticalResend() {
        when(exchange.claim(now)).thenReturn(Optional.of(message));
        doThrow(new IllegalStateException("database unavailable")).when(exchange).published(message, now);
        assertThatThrownBy(useCase::publishPending).isInstanceOf(IllegalStateException.class);
        verify(publisher).publish(message);
        verify(exchange, never()).failed(any(), any());
    }
}

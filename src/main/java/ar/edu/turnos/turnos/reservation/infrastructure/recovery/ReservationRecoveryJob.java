package ar.edu.turnos.turnos.reservation.infrastructure.recovery;

import ar.edu.turnos.turnos.reservation.domain.ports.in.RecoverPendingUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "turnos.recovery.enabled", havingValue = "true")
public class ReservationRecoveryJob {
    private static final Logger LOG = LoggerFactory.getLogger(ReservationRecoveryJob.class);
    private final RecoverPendingUseCase useCase;

    public ReservationRecoveryJob(RecoverPendingUseCase useCase) { this.useCase = useCase; }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() { run(); }

    @Scheduled(fixedDelayString = "${turnos.recovery.interval:60000}", initialDelayString = "${turnos.recovery.interval:60000}")
    public void scheduled() { run(); }

    private void run() {
        try { useCase.recover(); }
        catch (RuntimeException _) { LOG.warn("No se pudo completar el ciclo de recuperación; se reintentará"); }
    }
}

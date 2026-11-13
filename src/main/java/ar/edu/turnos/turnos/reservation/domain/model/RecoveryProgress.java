package ar.edu.turnos.turnos.reservation.domain.model;

import java.time.Instant;

public record RecoveryProgress(String processId, String phase, State state, int attempts,
                               Instant nextAttemptAt, Instant lastCheckedAt, String lastErrorCode) {
    public enum State { READY, LEASED, REVIEW, DONE }
}

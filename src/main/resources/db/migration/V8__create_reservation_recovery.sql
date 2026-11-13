CREATE TABLE reservation_recovery (
    process_id VARCHAR(36) PRIMARY KEY REFERENCES reservation_process(id) ON DELETE CASCADE,
    phase VARCHAR(32) NOT NULL,
    state VARCHAR(16) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_checked_at TIMESTAMPTZ,
    last_error_code VARCHAR(64)
);
CREATE INDEX idx_reservation_recovery_due ON reservation_recovery(next_attempt_at)
    WHERE state IN ('READY', 'LEASED');

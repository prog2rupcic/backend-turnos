ALTER TABLE reservation_process ADD COLUMN reservation_id BIGINT UNIQUE;
ALTER TABLE reservation_process ADD COLUMN confirmed_at TIMESTAMPTZ;
ALTER TABLE reservation_process ADD COLUMN rejection_reason VARCHAR(100);

CREATE TABLE reservation_phone_submission (
    event_id VARCHAR(100) PRIMARY KEY,
    process_id VARCHAR(36) NOT NULL REFERENCES reservation_process(id) ON DELETE CASCADE,
    group_id VARCHAR(100) NOT NULL,
    external_process_id VARCHAR(100) NOT NULL,
    hold_id VARCHAR(100) NOT NULL,
    request_event_id VARCHAR(100) NOT NULL,
    phone_number VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    delivery_status VARCHAR(32) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_phone_submission_pending ON reservation_phone_submission(next_attempt_at)
    WHERE delivery_status IN ('PENDING', 'IN_FLIGHT');

CREATE TABLE reservation_processed_event (
    event_id VARCHAR(100) PRIMARY KEY,
    process_id VARCHAR(36) NOT NULL REFERENCES reservation_process(id) ON DELETE CASCADE,
    occurred_at TIMESTAMPTZ NOT NULL
);
INSERT INTO reservation_processed_event(event_id, process_id, occurred_at)
    SELECT request_event_id, id, updated_at FROM reservation_process WHERE request_event_id IS NOT NULL;

DROP INDEX uq_reservation_process_unresolved_owner_slot;
CREATE UNIQUE INDEX uq_reservation_process_unresolved_owner_slot
    ON reservation_process(owner_user_id, professional_id, appointment_date, start_time)
    WHERE status IN ('HOLD_REQUESTING', 'HELD', 'HOLD_OUTCOME_UNKNOWN', 'CONFIRM_REQUESTING',
                     'CONFIRM_OUTCOME_UNKNOWN', 'WAITING_FOR_PHONE', 'PHONE_SUBMISSION_PENDING', 'PHONE_SUBMITTED');

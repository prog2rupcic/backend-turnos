CREATE TABLE reservation_process (
    id VARCHAR(36) PRIMARY KEY,
    owner_user_id BIGINT NOT NULL REFERENCES app_user(id),
    professional_id BIGINT NOT NULL,
    appointment_date DATE NOT NULL,
    start_time TIME NOT NULL,
    status VARCHAR(32) NOT NULL,
    hold_id VARCHAR(100) UNIQUE,
    external_process_id VARCHAR(100) UNIQUE,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL
);

CREATE INDEX idx_reservation_process_owner ON reservation_process (owner_user_id);
CREATE INDEX idx_reservation_process_slot ON reservation_process (professional_id, appointment_date, start_time);
CREATE UNIQUE INDEX uq_reservation_process_unresolved_owner_slot
    ON reservation_process (owner_user_id, professional_id, appointment_date, start_time)
    WHERE status IN ('HOLD_REQUESTING', 'HELD', 'HOLD_OUTCOME_UNKNOWN');

ALTER TABLE reservation_process ADD COLUMN cancelled_at TIMESTAMPTZ;
ALTER TABLE reservation_process ADD COLUMN cancellation_reason VARCHAR(500);

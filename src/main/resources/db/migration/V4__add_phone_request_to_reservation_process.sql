ALTER TABLE reservation_process ADD COLUMN request_event_id VARCHAR(100);
ALTER TABLE reservation_process ADD COLUMN request_required_field VARCHAR(32);
ALTER TABLE reservation_process ADD COLUMN request_message VARCHAR(2000);

ALTER TABLE reservation_process
    ADD CONSTRAINT uq_reservation_process_request_event UNIQUE (request_event_id);

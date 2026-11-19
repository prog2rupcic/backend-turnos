DROP INDEX uq_reservation_process_unresolved_owner_slot;

CREATE UNIQUE INDEX uq_reservation_process_unresolved_owner_slot
    ON reservation_process (owner_user_id, professional_id, appointment_date, start_time)
    WHERE status IN ('HOLD_REQUESTING', 'HELD', 'HOLD_OUTCOME_UNKNOWN', 'CONFIRM_REQUESTING',
                     'CONFIRM_OUTCOME_UNKNOWN', 'WAITING_FOR_PHONE');

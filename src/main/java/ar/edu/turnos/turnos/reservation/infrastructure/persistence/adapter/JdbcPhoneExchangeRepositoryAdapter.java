package ar.edu.turnos.turnos.reservation.infrastructure.persistence.adapter;

import ar.edu.turnos.turnos.reservation.domain.model.PhoneSubmission;
import ar.edu.turnos.turnos.reservation.domain.ports.out.PhoneExchangeRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcPhoneExchangeRepositoryAdapter implements PhoneExchangeRepository {
    private final JdbcTemplate jdbc;

    public JdbcPhoneExchangeRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void lockProcess(String processId) {
        jdbc.query("SELECT id FROM reservation_process WHERE id = ? OR external_process_id = ? FOR UPDATE",
                (row, _) -> row.getString(1), processId, processId);
    }

    @Override
    public boolean markEvent(String eventId, String processId, Instant now) {
        return jdbc.update("""
                INSERT INTO reservation_processed_event(event_id, process_id, occurred_at) VALUES (?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, processId, Timestamp.from(now)) == 1;
    }

    @Override
    public Optional<PhoneSubmission> latest(String processId) {
        return jdbc.query("""
                SELECT * FROM reservation_phone_submission WHERE process_id = ?
                ORDER BY occurred_at DESC, event_id DESC LIMIT 1
                """, (row, _) -> map(row), processId).stream().findFirst();
    }

    @Override
    public void enqueue(PhoneSubmission submission) {
        jdbc.update("""
                INSERT INTO reservation_phone_submission(event_id, process_id, group_id, external_process_id,
                    hold_id, request_event_id, phone_number, occurred_at, expires_at, delivery_status, next_attempt_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?)
                """, submission.eventId(), submission.processId(), submission.groupId(), submission.reservationProcessId(),
                submission.holdId(), submission.requestEventId(), submission.phoneNumber(),
                Timestamp.from(submission.occurredAt()), Timestamp.from(submission.expiresAt()),
                Timestamp.from(submission.occurredAt()));
    }

    @Override
    public void retry(String eventId, Instant now) {
        jdbc.update("""
                UPDATE reservation_phone_submission SET delivery_status='PENDING', attempts=0, next_attempt_at=?
                WHERE event_id=? AND delivery_status='RETRY_EXHAUSTED' AND expires_at > ?
                """, Timestamp.from(now), eventId, Timestamp.from(now));
    }

    @Override
    @Transactional
    public Optional<PhoneSubmission> claim(Instant now) {
        jdbc.update("""
                UPDATE reservation_phone_submission SET delivery_status='ABANDONED'
                WHERE delivery_status IN ('PENDING','IN_FLIGHT') AND expires_at <= ?
                """, Timestamp.from(now));
        jdbc.update("""
                UPDATE reservation_phone_submission SET delivery_status='RETRY_EXHAUSTED'
                WHERE delivery_status='IN_FLIGHT' AND attempts >= 5 AND next_attempt_at <= ?
                """, Timestamp.from(now));
        return jdbc.query("""
                UPDATE reservation_phone_submission SET delivery_status='IN_FLIGHT', attempts=attempts+1,
                    next_attempt_at=?
                WHERE event_id = (SELECT event_id FROM reservation_phone_submission
                    WHERE delivery_status IN ('PENDING','IN_FLIGHT') AND next_attempt_at <= ?
                      AND expires_at > ? AND attempts < 5
                    ORDER BY next_attempt_at FOR UPDATE SKIP LOCKED LIMIT 1)
                RETURNING *
                """, (row, _) -> map(row), Timestamp.from(now.plusSeconds(60)), Timestamp.from(now),
                Timestamp.from(now)).stream().findFirst();
    }

    @Override
    @Transactional
    public void published(PhoneSubmission submission, Instant now) {
        lockProcess(submission.processId());
        int changed = jdbc.update("""
                UPDATE reservation_phone_submission SET delivery_status='PUBLISHED'
                WHERE event_id=? AND delivery_status='IN_FLIGHT' AND attempts=?
                """, submission.eventId(), submission.attempts());
        if (changed == 1) {
            jdbc.update("""
                    UPDATE reservation_process SET status='PHONE_SUBMITTED', updated_at=?, version=version+1
                    WHERE id=? AND status='PHONE_SUBMISSION_PENDING'
                    """, Timestamp.from(now), submission.processId());
        }
    }

    @Override
    public void failed(PhoneSubmission submission, Instant now) {
        jdbc.update("""
                UPDATE reservation_phone_submission SET delivery_status=?, next_attempt_at=?
                WHERE event_id=? AND delivery_status='IN_FLIGHT' AND attempts=?
                """, submission.attempts() >= 5 ? "RETRY_EXHAUSTED" : "PENDING",
                Timestamp.from(now.plusSeconds(Math.min(30, 1L << submission.attempts()))),
                submission.eventId(), submission.attempts());
    }

    @Override
    public void resolve(String processId, PhoneSubmission.DeliveryStatus status) {
        jdbc.update("""
                UPDATE reservation_phone_submission SET delivery_status=? WHERE process_id=?
                AND delivery_status NOT IN ('REJECTED','COMPLETED')
                """, status.name(), processId);
    }

    @Override
    public void abandonPending(String processId) {
        jdbc.update("""
                UPDATE reservation_phone_submission SET delivery_status='ABANDONED'
                WHERE process_id=? AND delivery_status IN ('PENDING','IN_FLIGHT','RETRY_EXHAUSTED')
                """, processId);
    }

    private static PhoneSubmission map(ResultSet row) throws SQLException {
        return new PhoneSubmission(row.getString("event_id"), row.getString("process_id"),
                row.getString("group_id"), row.getString("external_process_id"), row.getString("hold_id"),
                row.getString("request_event_id"), row.getString("phone_number"),
                row.getTimestamp("occurred_at").toInstant(), row.getTimestamp("expires_at").toInstant(),
                PhoneSubmission.DeliveryStatus.valueOf(row.getString("delivery_status")), row.getInt("attempts"));
    }
}

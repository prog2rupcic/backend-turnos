package ar.edu.turnos.turnos.reservation.infrastructure.persistence.adapter;

import ar.edu.turnos.turnos.reservation.domain.model.RecoveryProgress;
import ar.edu.turnos.turnos.reservation.domain.ports.out.RecoveryRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JdbcRecoveryRepositoryAdapter implements RecoveryRepository {
    private final JdbcTemplate jdbc;

    public JdbcRecoveryRepositoryAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public void discover(Instant now) {
        jdbc.update("""
                UPDATE reservation_recovery r SET state='DONE', last_error_code=NULL
                WHERE state <> 'DONE' AND EXISTS (SELECT 1 FROM reservation_process p WHERE p.id=r.process_id
                  AND p.status IN ('CONFIRMED','CANCELLED','EXPIRED','INVALID','FAILED'))
                """);
        jdbc.update("""
                INSERT INTO reservation_recovery(process_id, phase, state, attempts, next_attempt_at)
                SELECT id, status, 'READY', 0, ? FROM reservation_process
                WHERE status IN ('HOLD_REQUESTING','HOLD_OUTCOME_UNKNOWN','CONFIRM_REQUESTING',
                                 'CONFIRM_OUTCOME_UNKNOWN','CANCEL_REQUESTING','CANCEL_OUTCOME_UNKNOWN')
                  AND updated_at <= ?
                   OR status IN ('HELD','WAITING_FOR_PHONE','PHONE_SUBMISSION_PENDING','PHONE_SUBMITTED')
                  AND expires_at IS NOT NULL AND expires_at <= ?
                ON CONFLICT (process_id) DO UPDATE SET phase=excluded.phase, state='READY', attempts=0,
                    next_attempt_at=excluded.next_attempt_at, last_error_code=NULL
                WHERE reservation_recovery.phase <> excluded.phase
                """, Timestamp.from(now), Timestamp.from(now.minusSeconds(10)), Timestamp.from(now));
    }

    @Override
    @Transactional
    public Optional<RecoveryProgress> claim(Instant now) {
        jdbc.update("""
                UPDATE reservation_recovery r SET state='REVIEW', last_error_code='ATTEMPTS_EXHAUSTED'
                WHERE state='LEASED' AND attempts >= 5 AND next_attempt_at <= ?
                  AND EXISTS (SELECT 1 FROM reservation_process p WHERE p.id=r.process_id AND p.status=r.phase)
                """, Timestamp.from(now));
        return jdbc.query("""
                UPDATE reservation_recovery SET state='LEASED', attempts=attempts+1,
                    next_attempt_at=?
                WHERE process_id=(SELECT r.process_id FROM reservation_recovery r
                    JOIN reservation_process p ON p.id=r.process_id AND p.status=r.phase
                    WHERE r.state IN ('READY','LEASED') AND r.next_attempt_at<=? AND r.attempts<5
                    ORDER BY r.next_attempt_at, r.process_id FOR UPDATE OF r SKIP LOCKED LIMIT 1)
                RETURNING *
                """, (row, _) -> map(row), Timestamp.from(now.plusSeconds(600)),
                Timestamp.from(now)).stream().findFirst();
    }

    @Override
    public Optional<RecoveryProgress> get(String processId) {
        return jdbc.query("SELECT * FROM reservation_recovery WHERE process_id=?", (row, _) -> map(row), processId)
                .stream().findFirst();
    }

    @Override
    public void reschedule(RecoveryProgress claim, Instant when, String code) {
        update(claim, claim.attempts() >= 5 ? "REVIEW" : "READY", when, code);
    }

    @Override
    public void review(RecoveryProgress claim, String code) {
        update(claim, "REVIEW", claim.nextAttemptAt(), code);
    }

    @Override
    public void done(RecoveryProgress claim) {
        update(claim, "DONE", claim.nextAttemptAt(), null);
    }

    private void update(RecoveryProgress claim, String state, Instant when, String code) {
        jdbc.update("""
                UPDATE reservation_recovery SET state=?, next_attempt_at=?, last_error_code=?, last_checked_at=?
                WHERE process_id=? AND phase=? AND state='LEASED' AND attempts=?
                """, state, Timestamp.from(when), code, Timestamp.from(Instant.now()),
                claim.processId(), claim.phase(), claim.attempts());
    }

    private static RecoveryProgress map(ResultSet row) throws SQLException {
        var last = row.getTimestamp("last_checked_at");
        return new RecoveryProgress(row.getString("process_id"), row.getString("phase"),
                RecoveryProgress.State.valueOf(row.getString("state")), row.getInt("attempts"),
                row.getTimestamp("next_attempt_at").toInstant(), last == null ? null : last.toInstant(),
                row.getString("last_error_code"));
    }
}

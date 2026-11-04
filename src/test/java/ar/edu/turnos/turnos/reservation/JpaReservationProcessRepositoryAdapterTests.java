package ar.edu.turnos.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.turnos.turnos.reservation.application.exception.HoldConfirmationException;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.infrastructure.persistence.adapter.JpaReservationProcessRepositoryAdapter;
import ar.edu.turnos.turnos.reservation.infrastructure.persistence.entity.ReservationProcessEntity;
import ar.edu.turnos.turnos.reservation.infrastructure.persistence.mapper.ReservationProcessMapper;
import ar.edu.turnos.turnos.reservation.infrastructure.persistence.repository.JpaReservationProcessRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class JpaReservationProcessRepositoryAdapterTests {
    @Test
    void translatesOptimisticLockConflictIntoConcurrentConfirmationOutcome() {
        JpaReservationProcessRepository repository = mock(JpaReservationProcessRepository.class);
        ReservationProcessMapper mapper = mock(ReservationProcessMapper.class);
        when(mapper.toEntity(org.mockito.ArgumentMatchers.any(ReservationProcess.class)))
                .thenAnswer(invocation -> ReservationProcessEntity.from(invocation.getArgument(0)));
        when(repository.saveAndFlush(any(ReservationProcessEntity.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(ReservationProcessEntity.class, "local-id"));
        var adapter = new JpaReservationProcessRepositoryAdapter(repository, mapper);

        assertThatThrownBy(() -> adapter.save(process()))
                .isInstanceOfSatisfying(HoldConfirmationException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.reason())
                                .isEqualTo(HoldConfirmationException.Reason.CONFIRMATION_IN_PROGRESS));
    }

    private static ReservationProcess process() {
        Instant now = Instant.parse("2026-11-03T17:00:00Z");
        return new ReservationProcess("local-id", 42, 15, LocalDate.of(2026, 11, 9),
                LocalTime.of(9, 30), ReservationProcess.Status.CONFIRM_REQUESTING,
                "hold-id", "external-id", now.plusSeconds(300), null, null, null, now, now, 2L);
    }
}

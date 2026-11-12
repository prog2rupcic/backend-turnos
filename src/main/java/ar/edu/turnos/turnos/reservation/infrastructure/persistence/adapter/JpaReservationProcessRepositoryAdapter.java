package ar.edu.turnos.turnos.reservation.infrastructure.persistence.adapter;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.application.exception.HoldCreationException;
import ar.edu.turnos.turnos.reservation.application.exception.HoldConfirmationException;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import ar.edu.turnos.turnos.reservation.infrastructure.persistence.mapper.ReservationProcessMapper;
import ar.edu.turnos.turnos.reservation.infrastructure.persistence.repository.JpaReservationProcessRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.sql.SQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
public class JpaReservationProcessRepositoryAdapter implements ReservationProcessRepository {
    private final JpaReservationProcessRepository repository;
    private final ReservationProcessMapper mapper;

    public JpaReservationProcessRepositoryAdapter(JpaReservationProcessRepository repository,
                                                   ReservationProcessMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public ReservationProcess save(ReservationProcess process) {
        try {
            return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(process)));
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new HoldConfirmationException(HoldConfirmationException.Reason.CONFIRMATION_IN_PROGRESS);
        } catch (DataIntegrityViolationException exception) {
            if (process.version() == null && exception.getMostSpecificCause() instanceof SQLException sql
                    && "23505".equals(sql.getSQLState())) {
                throw new HoldCreationException(HoldCreationException.Reason.ALREADY_IN_PROGRESS);
            }
            throw exception;
        }
    }

    @Override
    public Optional<ReservationProcess> findById(String id) {
        return repository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<ReservationProcess> findByReservationProcessId(String reservationProcessId) {
        return repository.findByReservationProcessId(reservationProcessId).map(mapper::toDomain);
    }

    @Override
    public Map<String, ReservationProcess> findByReservationProcessIds(Collection<String> ids) {
        if (ids.isEmpty()) { return Map.of(); }
        return repository.findByReservationProcessIdIn(ids).stream().map(mapper::toDomain)
                .collect(Collectors.toMap(ReservationProcess::reservationProcessId, process -> process));
    }

    @Override
    public Optional<ReservationProcess> findUnresolved(long ownerUserId, long professionalId, LocalDate date,
                                                        LocalTime startTime) {
        return repository.findFirstByOwnerUserIdAndProfessionalIdAndDateAndStartTimeAndStatusInOrderByCreatedAtDesc(
                ownerUserId, professionalId, date, startTime, List.of(ReservationProcess.Status.HOLD_REQUESTING,
                        ReservationProcess.Status.HELD, ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN,
                        ReservationProcess.Status.CONFIRM_REQUESTING,
                        ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN,
                        ReservationProcess.Status.WAITING_FOR_PHONE,
                        ReservationProcess.Status.PHONE_SUBMISSION_PENDING,
                        ReservationProcess.Status.PHONE_SUBMITTED))
                .map(mapper::toDomain);
    }
}

package ar.edu.turnos.turnos.reservation.application.usecases;

import ar.edu.turnos.turnos.reservation.domain.model.OwnReservation;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationOperationException;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationPage;
import ar.edu.turnos.turnos.reservation.domain.ports.in.ListOwnReservationsUseCase;
import ar.edu.turnos.turnos.reservation.domain.ports.out.CourseReservationsGateway;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ListOwnReservationsUseCaseImpl implements ListOwnReservationsUseCase {
    private static final int REMOTE_PAGE_SIZE = 100;
    private static final int MAX_REMOTE_PAGES = 100;
    private final CourseReservationsGateway gateway;
    private final ReservationProcessRepository processes;

    public ListOwnReservationsUseCaseImpl(CourseReservationsGateway gateway, ReservationProcessRepository processes) {
        this.gateway = gateway;
        this.processes = processes;
    }

    @Override
    public ReservationPage list(long ownerUserId, int page, int size) {
        if (ownerUserId <= 0 || page < 0 || size < 1 || size > 100 || (long) page * size > 10_000) {
            throw new IllegalArgumentException("Paginación inválida");
        }
        List<OwnReservation> own = new ArrayList<>();
        String patientId = "user-" + ownerUserId;
        long total;
        Long expectedTotal = null;
        int remotePage = 0;
        do {
            var result = gateway.list(patientId, remotePage, REMOTE_PAGE_SIZE);
            total = result.totalElements();
            long offset = (long) remotePage * REMOTE_PAGE_SIZE;
            if (total < 0 || total > 10_000 || expectedTotal != null && total != expectedTotal
                    || result.returnedCount() != Math.min(REMOTE_PAGE_SIZE, Math.max(0, total - offset))) {
                throw new ReservationOperationException(ReservationOperationException.Reason.INVALID_RESPONSE);
            }
            expectedTotal = total;
            var byId = processes.findByReservationProcessIds(result.content().stream()
                    .map(OwnReservation::processId).toList());
            for (var item : result.content()) {
                var local = byId.get(item.processId());
                if (local != null && local.ownerUserId() == ownerUserId && local.professionalId() == item.professionalId()
                        && local.date().equals(item.date()) && local.startTime().equals(item.startTime())
                        && (local.reservationId() == null || local.reservationId() == item.reservationId())) {
                    own.add(new OwnReservation(local.id(), item.reservationId(), item.professionalId(), item.date(),
                            item.startTime(), item.endTime(), item.professionalFirstName(), item.professionalLastName(),
                            item.status(), item.confirmedAt(), item.cancelledAt(), item.cancellationReason()));
                }
            }
            remotePage++;
        } while ((long) remotePage * REMOTE_PAGE_SIZE < total && remotePage < MAX_REMOTE_PAGES);
        if ((long) remotePage * REMOTE_PAGE_SIZE < total) {
            throw new ReservationOperationException(ReservationOperationException.Reason.INTEGRATION_UNAVAILABLE);
        }
        int start = (int) Math.min((long) page * size, own.size());
        int end = Math.min(start + size, own.size());
        return new ReservationPage(List.copyOf(own.subList(start, end)), own.size(), page, size);
    }
}

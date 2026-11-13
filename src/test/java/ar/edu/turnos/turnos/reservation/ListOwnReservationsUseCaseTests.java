package ar.edu.turnos.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import ar.edu.turnos.turnos.reservation.application.usecases.ListOwnReservationsUseCaseImpl;
import ar.edu.turnos.turnos.reservation.domain.model.OwnReservation;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.domain.ports.out.CourseReservationsGateway;
import ar.edu.turnos.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ListOwnReservationsUseCaseTests {
    @Test
    void scansAllRemotePagesBeforePaginatingAfterLocalOwnershipFiltering() {
        var course = mock(CourseReservationsGateway.class);
        var processes = mock(ReservationProcessRepository.class);
        var remote = new ArrayList<OwnReservation>();
        for (int i = 0; i < 100; i++) { remote.add(item("process-foreign-" + i)); }
        remote.set(0, item("own-first"));
        when(course.list("user-42", 0, 100)).thenReturn(new CourseReservationsGateway.Page(remote, 101, 100));
        when(course.list("user-42", 1, 100)).thenReturn(new CourseReservationsGateway.Page(
                List.of(item("own-second")), 101, 1));
        when(processes.findByReservationProcessIds(anyCollection())).thenAnswer(invocation -> {
            java.util.Collection<String> ids = invocation.getArgument(0);
            var result = new java.util.HashMap<String, ReservationProcess>();
            if (ids.contains("own-first")) { result.put("own-first", process("local-first", "own-first")); }
            if (ids.contains("own-second")) { result.put("own-second", process("local-second", "own-second")); }
            return result;
        });
        var result = new ListOwnReservationsUseCaseImpl(course, processes).list(42, 1, 1);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content()).extracting(OwnReservation::processId).containsExactly("local-second");
        verify(course).list("user-42", 1, 100);
    }

    private static OwnReservation item(String id) {
        return new OwnReservation(id, 8251, 15, LocalDate.of(2026, 11, 14), LocalTime.of(9, 30),
                LocalTime.of(10, 0), "Ana", "Gomez", "CONFIRMED", Instant.now(), null, null);
    }

    private static ReservationProcess process(String id, String external) {
        var now = Instant.now();
        return new ReservationProcess(id, 42, 15, LocalDate.of(2026, 11, 14), LocalTime.of(9, 30),
                ReservationProcess.Status.HELD, "hold-" + id, external, now.plusSeconds(600),
                "request-" + id, "PHONE_NUMBER", null, now, now, null).confirmed(8251, now, now);
    }
}

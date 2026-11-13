package ar.edu.turnos.turnos.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import ar.edu.turnos.turnos.reservation.application.usecases.ListOwnReservationsUseCaseImpl;
import ar.edu.turnos.turnos.reservation.domain.model.OwnReservation;
import ar.edu.turnos.turnos.reservation.domain.model.ReservationOperationException;
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
import org.mockito.ArgumentCaptor;

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

    @Test
    void anInvalidPageOrSizeIsRejected() {
        var useCase = new ListOwnReservationsUseCaseImpl(mock(CourseReservationsGateway.class),
                mock(ReservationProcessRepository.class));

        assertThatThrownBy(() -> useCase.list(42, -1, 20)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.list(42, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.list(42, 0, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.list(0, 0, 20)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aListingThatDoesNotMatchItsTotalIsRejected() {
        var course = mock(CourseReservationsGateway.class);
        var processes = mock(ReservationProcessRepository.class);
        when(course.list("user-42", 0, 100)).thenReturn(new CourseReservationsGateway.Page(List.of(), 5, 0));

        assertThatThrownBy(() -> new ListOwnReservationsUseCaseImpl(course, processes).list(42, 0, 20))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.INVALID_RESPONSE));
    }

    @Test
    void aTotalThatChangesBetweenPagesIsRejected() {
        var course = mock(CourseReservationsGateway.class);
        var processes = mock(ReservationProcessRepository.class);
        when(course.list("user-42", 0, 100)).thenReturn(
                new CourseReservationsGateway.Page(List.of(), 150, 100));
        when(course.list("user-42", 1, 100)).thenReturn(
                new CourseReservationsGateway.Page(List.of(), 120, 20));

        assertThatThrownBy(() -> new ListOwnReservationsUseCaseImpl(course, processes).list(42, 0, 20))
                .isInstanceOfSatisfying(ReservationOperationException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(ReservationOperationException.Reason.INVALID_RESPONSE));
    }

    @Test
    void aReservationOfAnotherOwnerIsNotReturned() {
        var course = mock(CourseReservationsGateway.class);
        var processes = mock(ReservationProcessRepository.class);
        when(course.list("user-42", 0, 100)).thenReturn(
                new CourseReservationsGateway.Page(List.of(item("process-1")), 1, 1));
        when(processes.findByReservationProcessIds(anyCollection())).thenReturn(Map.of(
                "process-1", process("local-1", "process-1", 99)));

        assertThat(new ListOwnReservationsUseCaseImpl(course, processes).list(42, 0, 20).totalElements())
                .isZero();
    }

    @Test
    void aReservationWhoseSlotDiffersIsNotReturned() {
        var course = mock(CourseReservationsGateway.class);
        var processes = mock(ReservationProcessRepository.class);
        when(course.list("user-42", 0, 100)).thenReturn(
                new CourseReservationsGateway.Page(List.of(item("process-1")), 1, 1));
        when(processes.findByReservationProcessIds(anyCollection())).thenReturn(Map.of(
                "process-1", processInAnotherSlot("local-1", "process-1")));

        assertThat(new ListOwnReservationsUseCaseImpl(course, processes).list(42, 0, 20).totalElements())
                .isZero();
    }

    @Test
    void aReservationWithAnotherKnownReservationIdIsNotReturned() {
        var course = mock(CourseReservationsGateway.class);
        var processes = mock(ReservationProcessRepository.class);
        when(course.list("user-42", 0, 100)).thenReturn(
                new CourseReservationsGateway.Page(List.of(item("process-1")), 1, 1));
        when(processes.findByReservationProcessIds(anyCollection())).thenReturn(Map.of(
                "process-1", processWithAnotherReservationId("local-1", "process-1")));

        assertThat(new ListOwnReservationsUseCaseImpl(course, processes).list(42, 0, 20).totalElements())
                .isZero();
    }

    @Test
    void theLocalIdentifierIsReturnedInsteadOfTheCourseOne() {
        var course = mock(CourseReservationsGateway.class);
        var processes = mock(ReservationProcessRepository.class);
        when(course.list("user-42", 0, 100)).thenReturn(
                new CourseReservationsGateway.Page(List.of(item("process-1")), 1, 1));
        when(processes.findByReservationProcessIds(anyCollection())).thenReturn(Map.of(
                "process-1", process("local-1", "process-1", 42)));

        var page = new ListOwnReservationsUseCaseImpl(course, processes).list(42, 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).extracting(OwnReservation::processId).containsExactly("local-1");
    }

    @Test
    void aPageBeyondTheAvailableContentIsEmpty() {
        var course = mock(CourseReservationsGateway.class);
        var processes = mock(ReservationProcessRepository.class);
        when(course.list("user-42", 0, 100)).thenReturn(
                new CourseReservationsGateway.Page(List.of(item("process-1")), 1, 1));
        when(processes.findByReservationProcessIds(anyCollection())).thenReturn(Map.of(
                "process-1", process("local-1", "process-1", 42)));

        var page = new ListOwnReservationsUseCaseImpl(course, processes).list(42, 5, 20);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void thePatientIdentifierIsAlwaysDerivedFromTheAuthenticatedUser() {
        var course = mock(CourseReservationsGateway.class);
        var processes = mock(ReservationProcessRepository.class);
        when(course.list("user-42", 0, 100)).thenReturn(
                new CourseReservationsGateway.Page(List.of(), 0, 0));

        new ListOwnReservationsUseCaseImpl(course, processes).list(42, 0, 20);

        var captor = ArgumentCaptor.forClass(String.class);
        verify(course).list(captor.capture(), anyInt(), anyInt());
        assertThat(captor.getValue()).isEqualTo("user-42");
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

    private static ReservationProcess process(String id, String external, long owner) {
        var now = Instant.now();
        return new ReservationProcess(id, owner, 15, LocalDate.of(2026, 11, 14), LocalTime.of(9, 30),
                ReservationProcess.Status.HELD, "hold-" + id, external, now.plusSeconds(600),
                "request-" + id, "PHONE_NUMBER", null, now, now, null).confirmed(8251, now, now);
    }

    private static ReservationProcess processInAnotherSlot(String id, String external) {
        var now = Instant.now();
        return new ReservationProcess(id, 42, 15, LocalDate.of(2026, 11, 15), LocalTime.of(11, 0),
                ReservationProcess.Status.HELD, "hold-" + id, external, now.plusSeconds(600),
                "request-" + id, "PHONE_NUMBER", null, now, now, null).confirmed(8251, now, now);
    }

    private static ReservationProcess processWithAnotherReservationId(String id, String external) {
        var now = Instant.now();
        return new ReservationProcess(id, 42, 15, LocalDate.of(2026, 11, 14), LocalTime.of(9, 30),
                ReservationProcess.Status.HELD, "hold-" + id, external, now.plusSeconds(600),
                "request-" + id, "PHONE_NUMBER", null, now, now, null).confirmed(9999, now, now);
    }
}

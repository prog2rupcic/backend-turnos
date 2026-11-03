package ar.edu.turnos.turnos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import ar.edu.turnos.turnos.reservation.infrastructure.persistence.repository.JpaReservationProcessRepository;
import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ReservationHttpTests extends AbstractIntegrationTest {
    private static final HttpServer CATALOG = server();
    private static final HttpServer CATEDRA = server();
    private static final AtomicReference<Reply> CATALOG_REPLY = new AtomicReference<>();
    private static final AtomicReference<Reply> OCCUPANCIES_REPLY = new AtomicReference<>();
    private static final AtomicReference<Reply> HOLD_REPLY = new AtomicReference<>();
    private static final AtomicReference<Reply> CONFIRM_REPLY = new AtomicReference<>();
    private static final AtomicReference<String> CATALOG_AUTH = new AtomicReference<>();
    private static final AtomicReference<String> OCCUPANCIES_AUTH = new AtomicReference<>();
    private static final AtomicReference<String> HOLD_AUTH = new AtomicReference<>();
    private static final AtomicReference<String> HOLD_BODY = new AtomicReference<>();
    private static final AtomicReference<String> CONFIRM_AUTH = new AtomicReference<>();
    private static final AtomicReference<String> CONFIRM_BODY = new AtomicReference<>();
    private static final AtomicReference<String> CONFIRM_PATH = new AtomicReference<>();
    private static final AtomicInteger OCCUPANCY_CALLS = new AtomicInteger();
    private static final AtomicInteger HOLD_CALLS = new AtomicInteger();
    private static final AtomicInteger CONFIRM_CALLS = new AtomicInteger();
    private static final AtomicReference<JpaReservationProcessRepository> PROCESS_REPOSITORY = new AtomicReference<>();
    private static final AtomicReference<ReservationProcess.Status> STATUS_AT_POST = new AtomicReference<>();
    private static final AtomicReference<ReservationProcess.Status> STATUS_AT_CONFIRM = new AtomicReference<>();

    static {
        CATALOG.createContext("/api/professionals/", request -> {
            CATALOG_AUTH.set(request.getRequestHeaders().getFirst("Authorization"));
            send(request, CATALOG_REPLY.get());
        });
        CATEDRA.createContext("/api/appointment-occupancies", request -> {
            OCCUPANCIES_AUTH.set(request.getRequestHeaders().getFirst("Authorization"));
            OCCUPANCY_CALLS.incrementAndGet();
            send(request, OCCUPANCIES_REPLY.get());
        });
        CATEDRA.createContext("/api/appointment-holds", request -> {
            STATUS_AT_POST.set(PROCESS_REPOSITORY.get().findAll().getFirst().getStatus());
            HOLD_AUTH.set(request.getRequestHeaders().getFirst("Authorization"));
            HOLD_BODY.set(new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            HOLD_CALLS.incrementAndGet();
            send(request, HOLD_REPLY.get());
        });
        CATEDRA.createContext("/api/appointment-holds/", request -> {
            CONFIRM_AUTH.set(request.getRequestHeaders().getFirst("Authorization"));
            CONFIRM_BODY.set(new String(request.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            CONFIRM_PATH.set(request.getRequestURI().getPath());
            STATUS_AT_CONFIRM.set(PROCESS_REPOSITORY.get().findAll().getFirst().getStatus());
            CONFIRM_CALLS.incrementAndGet();
            send(request, CONFIRM_REPLY.get());
        });
        CATALOG.start();
        CATEDRA.start();
    }

    @DynamicPropertySource
    static void remoteProperties(DynamicPropertyRegistry registry) {
        registry.add("turnos.catalog.base-url", () -> "http://localhost:" + CATALOG.getAddress().getPort());
        registry.add("turnos.catedra.base-url", () -> "http://localhost:" + CATEDRA.getAddress().getPort());
        registry.add("turnos.catedra.technical-jwt", () -> "test-only-technical-jwt");
    }

    @AfterAll
    static void stopServers() {
        CATALOG.stop(0);
        CATEDRA.stop(0);
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JpaReservationProcessRepository processes;

    private LocalDate date;
    private String jwt;
    private long ownerId;

    @BeforeEach
    void prepare() throws Exception {
        processes.deleteAll();
        PROCESS_REPOSITORY.set(processes);
        STATUS_AT_POST.set(null);
        date = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).plusDays(7);
        CATALOG_REPLY.set(new Reply(200, """
                {"id":15,"enabled":true,"category":{"enabled":true},"weeklySchedules":[
                {"dayOfWeek":"%s","startTime":"09:00","endTime":"11:00","slotDurationMinutes":30}]}
                """.formatted(date.getDayOfWeek())));
        OCCUPANCIES_REPLY.set(new Reply(200, emptyOccupancies()));
        HOLD_REPLY.set(new Reply(201, """
                {"holdId":"hold-test","reservationProcessId":"process-test","status":"HELD","expiresAt":"%s"}
                """.formatted(Instant.now().plusSeconds(600))));
        CONFIRM_REPLY.set(new Reply(202, """
                {"reservationProcessId":"process-test","status":"WAITING_FOR_PHONE","expiresAt":"%s"}
                """.formatted(Instant.now().plusSeconds(600))));
        CATALOG_AUTH.set(null);
        OCCUPANCIES_AUTH.set(null);
        HOLD_AUTH.set(null);
        HOLD_BODY.set(null);
        CONFIRM_AUTH.set(null);
        CONFIRM_BODY.set(null);
        CONFIRM_PATH.set(null);
        CONFIRM_CALLS.set(0);
        STATUS_AT_CONFIRM.set(null);
        OCCUPANCY_CALLS.set(0);
        HOLD_CALLS.set(0);
        String username = "hold-" + UUID.randomUUID().toString().substring(0, 8);
        var registration = mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"login":"%s","password":"clave-correcta","firstName":"Ana",
                "lastName":"Garcia","email":"%s@example.com"}
                """.formatted(username, username))).andExpect(status().isCreated()).andReturn().getResponse();
        ownerId = ((Number) JsonPath.read(registration.getContentAsString(), "$.id")).longValue();
        var login = mvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"%s","password":"clave-correcta"}
                """.formatted(username))).andExpect(status().isOk()).andReturn().getResponse();
        jwt = JsonPath.read(login.getContentAsString(), "$.id_token");
    }

    @Test
    void createsHoldWithTechnicalJwtAndPersistsTheAuthenticatedOwner() throws Exception {
        mvc.perform(createRequest().header("Authorization", "Bearer " + jwt))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.holdId").value("hold-test"))
                .andExpect(jsonPath("$.reservationProcessId").value("process-test"));

        assertThat(CATALOG_AUTH.get()).isEqualTo("Bearer " + jwt);
        assertThat(OCCUPANCIES_AUTH.get()).isEqualTo("Bearer test-only-technical-jwt");
        assertThat(HOLD_AUTH.get()).isEqualTo("Bearer test-only-technical-jwt");
        assertThat(HOLD_BODY.get()).contains("\"externalPatientId\":\"user-" + ownerId + "\"",
                "\"startTime\":\"09:30\"");
        var saved = processes.findAll().getFirst();
        assertThat(saved.getOwnerUserId()).isEqualTo(ownerId);
        assertThat(saved.getStatus()).isEqualTo(ReservationProcess.Status.HELD);
        assertThat(saved.getHoldId()).isEqualTo("hold-test");
        assertThat(saved.getReservationProcessId()).isEqualTo("process-test");
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        assertThat(STATUS_AT_POST.get()).isEqualTo(ReservationProcess.Status.HOLD_REQUESTING);
        assertThat(HOLD_CALLS.get()).isEqualTo(1);

        mvc.perform(createRequest().header("Authorization", "Bearer " + jwt))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_IN_PROGRESS"))
                .andExpect(jsonPath("$.processId").value(saved.getId()));
        assertThat(HOLD_CALLS.get()).isEqualTo(1);
    }

    @Test
    void confirmsOwnHoldWithTheStoredUserProfileAndDoesNotRepeatCompletedConfirmation() throws Exception {
        String processId = createHeldProcess();
        mvc.perform(confirmRequest(processId).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(processId))
                .andExpect(jsonPath("$.status").value("WAITING_FOR_PHONE"));

        assertThat(CONFIRM_AUTH.get()).isEqualTo("Bearer test-only-technical-jwt");
        assertThat(CONFIRM_PATH.get()).isEqualTo("/api/appointment-holds/hold-test/confirm");
        assertThat(CONFIRM_BODY.get()).contains("\"externalPatientId\":\"user-" + ownerId + "\"",
                "\"patientFirstName\":\"Ana\"", "\"patientLastName\":\"Garcia\"");
        assertThat(STATUS_AT_CONFIRM.get()).isEqualTo(ReservationProcess.Status.CONFIRM_REQUESTING);
        assertThat(processes.findAll().getFirst().getStatus()).isEqualTo(ReservationProcess.Status.WAITING_FOR_PHONE);
        assertThat(CONFIRM_CALLS.get()).isEqualTo(1);

        mvc.perform(confirmRequest(processId).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("WAITING_FOR_PHONE"));
        assertThat(CONFIRM_CALLS.get()).isEqualTo(1);

        mvc.perform(createRequest().header("Authorization", "Bearer " + jwt))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.processId").value(processId));
        assertThat(HOLD_CALLS.get()).isEqualTo(1);
    }

    @Test
    void onlyTheProcessOwnerCanConfirmTheHold() throws Exception {
        String processId = createHeldProcess();
        String otherJwt = registerAndLogin("other-" + UUID.randomUUID().toString().substring(0, 8));

        mvc.perform(confirmRequest(processId).header("Authorization", "Bearer " + otherJwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PROCESS_NOT_OWNED"));
        assertThat(CONFIRM_CALLS.get()).isZero();
        assertThat(processes.findAll().getFirst().getStatus()).isEqualTo(ReservationProcess.Status.HELD);
    }

    @Test
    void confirmationTimeoutIsStoredAsUnknownAndExplicitRetryCanConvergeOnAlreadyConfirmed() throws Exception {
        String processId = createHeldProcess();
        CONFIRM_REPLY.set(new Reply(500, "{}"));

        mvc.perform(confirmRequest(processId).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("OUTCOME_UNKNOWN"))
                .andExpect(jsonPath("$.processId").value(processId));
        assertThat(processes.findAll().getFirst().getStatus())
                .isEqualTo(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN);

        CONFIRM_REPLY.set(new Reply(409, "{\"code\":\"RESERVATION_PROCESS_ALREADY_CONFIRMED\"}"));
        mvc.perform(confirmRequest(processId).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("WAITING_FOR_PHONE"));
        assertThat(processes.findAll().getFirst().getStatus()).isEqualTo(ReservationProcess.Status.WAITING_FOR_PHONE);
        assertThat(CONFIRM_CALLS.get()).isEqualTo(2);
    }

    @Test
    void expiredHoldBecomesTerminalAndCannotBeConfirmedAgain() throws Exception {
        String processId = createHeldProcess();
        CONFIRM_REPLY.set(new Reply(404, "{\"code\":\"HOLD_EXPIRED\"}"));

        mvc.perform(confirmRequest(processId).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));
        assertThat(processes.findAll().getFirst().getStatus()).isEqualTo(ReservationProcess.Status.EXPIRED);
        mvc.perform(confirmRequest(processId).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isGone());
        assertThat(CONFIRM_CALLS.get()).isEqualTo(1);
    }

    @Test
    void unavailableTechnicalCredentialsRestoreHeldStateAndMissingJwtIsRejected() throws Exception {
        String processId = createHeldProcess();
        CONFIRM_REPLY.set(new Reply(401, "{}"));
        mvc.perform(confirmRequest(processId).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INTEGRATION_UNAVAILABLE"));
        assertThat(processes.findAll().getFirst().getStatus()).isEqualTo(ReservationProcess.Status.HELD);

        mvc.perform(confirmRequest(processId)).andExpect(status().isUnauthorized());
        assertThat(CONFIRM_CALLS.get()).isEqualTo(1);
    }

    @Test
    void csrfExemptionIsLimitedToTheExactConfirmationPostPathAndMethod() throws Exception {
        mvc.perform(post("/api/reservation-processes/local-id/not-confirm")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reservation-processes/local-id/confirm")
                        .header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/reservation-processes").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedAcceptedResponseIsKeptAsAnUnknownOutcome() throws Exception {
        String processId = createHeldProcess();
        CONFIRM_REPLY.set(new Reply(202, "{\"reservationProcessId\":\"different-process\","
                + "\"status\":\"WAITING_FOR_PHONE\",\"expiresAt\":\"" + Instant.now().plusSeconds(600) + "\"}"));

        mvc.perform(confirmRequest(processId).header("Authorization", "Bearer " + jwt))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("OUTCOME_UNKNOWN"));
        assertThat(processes.findAll().getFirst().getStatus())
                .isEqualTo(ReservationProcess.Status.CONFIRM_OUTCOME_UNKNOWN);
    }

    @Test
    void slotOccupiedOrDisabledDoesNotCreateAProcess() throws Exception {
        OCCUPANCIES_REPLY.set(new Reply(200, """
                {"professionalId":15,"from":"%s","to":"%s","occupancies":[
                {"date":"%s","startTime":"09:30","status":"HELD"}]}
                """.formatted(date, date, date)));
        mvc.perform(createRequest().header("Authorization", "Bearer " + jwt))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));
        assertThat(processes.count()).isZero();
        assertThat(HOLD_CALLS.get()).isZero();
    }

    @Test
    void courseConflictMarksIntentFailedWithoutRetrying() throws Exception {
        HOLD_REPLY.set(new Reply(409, """
                {"code":"SLOT_ALREADY_HELD","detail":"slot in use"}
                """));
        mvc.perform(createRequest().header("Authorization", "Bearer " + jwt))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));
        assertThat(processes.findAll().getFirst().getStatus()).isEqualTo(ReservationProcess.Status.FAILED);
        assertThat(HOLD_CALLS.get()).isEqualTo(1);
    }

    @Test
    void courseNotFoundAndTechnicalAuthFailureHaveDistinctUserErrors() throws Exception {
        HOLD_REPLY.set(new Reply(404, "{\"code\":\"PROFESSIONAL_NOT_FOUND\"}"));
        mvc.perform(createRequest().header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFESSIONAL_NOT_FOUND"));
        assertThat(processes.findAll().getFirst().getStatus()).isEqualTo(ReservationProcess.Status.FAILED);
        processes.deleteAll();
        HOLD_REPLY.set(new Reply(401, "{}"));
        mvc.perform(createRequest().header("Authorization", "Bearer " + jwt))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("INTEGRATION_UNAVAILABLE"));
        assertThat(processes.findAll().getFirst().getStatus()).isEqualTo(ReservationProcess.Status.FAILED);
        assertThat(HOLD_CALLS.get()).isEqualTo(2);
    }

    @Test
    void serverErrorOrUnexpectedSuccessCannotProveTheHoldWasNotCreated() throws Exception {
        HOLD_REPLY.set(new Reply(500, "{}"));
        mvc.perform(createRequest().header("Authorization", "Bearer " + jwt))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("OUTCOME_UNKNOWN"));
        assertThat(processes.findAll().getFirst().getStatus())
                .isEqualTo(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN);
        processes.deleteAll();
        HOLD_REPLY.set(new Reply(200, """
                {"holdId":"not-a-201","reservationProcessId":"process-x","status":"HELD","expiresAt":"%s"}
                """.formatted(Instant.now().plusSeconds(600))));
        mvc.perform(createRequest().header("Authorization", "Bearer " + jwt))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("OUTCOME_UNKNOWN"));
        assertThat(processes.findAll().getFirst().getStatus())
                .isEqualTo(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN);
        assertThat(HOLD_CALLS.get()).isEqualTo(2);
    }

    @Test
    void uncertainResponsePersistsIntentAndRechecksOccupanciesWithoutRetrying() throws Exception {
        HOLD_REPLY.set(new Reply(201, "{}"));
        mvc.perform(createRequest().header("Authorization", "Bearer " + jwt))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("OUTCOME_UNKNOWN"))
                .andExpect(jsonPath("$.processId").isNotEmpty());
        var saved = processes.findAll().getFirst();
        assertThat(saved.getOwnerUserId()).isEqualTo(ownerId);
        assertThat(saved.getStatus()).isEqualTo(ReservationProcess.Status.HOLD_OUTCOME_UNKNOWN);
        assertThat(saved.getHoldId()).isNull();
        assertThat(OCCUPANCY_CALLS.get()).isEqualTo(2);
        assertThat(HOLD_CALLS.get()).isEqualTo(1);

        mvc.perform(createRequest().header("Authorization", "Bearer " + jwt))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_IN_PROGRESS"))
                .andExpect(jsonPath("$.processId").value(saved.getId()));
        assertThat(HOLD_CALLS.get()).isEqualTo(1);
    }

    @Test
    void differentUsersOwnSeparateAttemptsAndCannotChooseTheirIdentity() throws Exception {
        mvc.perform(createRequest().header("Authorization", "Bearer " + jwt))
                .andExpect(status().isCreated());
        var firstProcess = processes.findAll().getFirst();
        String secondUser = "hold-" + UUID.randomUUID().toString().substring(0, 8);
        var response = mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"login":"%s","password":"clave-correcta","firstName":"Otra",
                "lastName":"Persona","email":"%s@example.com"}
                """.formatted(secondUser, secondUser))).andExpect(status().isCreated()).andReturn().getResponse();
        long secondUserId = ((Number) JsonPath.read(response.getContentAsString(), "$.id")).longValue();
        var secondLogin = mvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"%s","password":"clave-correcta"}
                """.formatted(secondUser))).andExpect(status().isOk()).andReturn().getResponse();
        String secondJwt = JsonPath.read(secondLogin.getContentAsString(), "$.id_token");
        HOLD_REPLY.set(new Reply(201, """
                {"holdId":"hold-second","reservationProcessId":"process-second","status":"HELD","expiresAt":"%s"}
                """.formatted(Instant.now().plusSeconds(600))));
        mvc.perform(post("/api/reservation-processes").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + secondJwt).content("""
                        {"professionalId":15,"date":"%s","startTime":"09:30","ownerUserId":%d,
                         "externalPatientId":"user-%d"}
                        """.formatted(date, ownerId, ownerId)))
                .andExpect(status().isCreated());
        assertThat(firstProcess.getOwnerUserId()).isEqualTo(ownerId);
        assertThat(processes.findAll()).extracting("ownerUserId").containsExactlyInAnyOrder(ownerId, secondUserId);
        assertThat(HOLD_BODY.get()).contains("\"externalPatientId\":\"user-" + secondUserId + "\"");
    }

    @Test
    void unavailableCatalogAndInvalidRequestDoNotSendHold() throws Exception {
        CATALOG_REPLY.set(new Reply(503, "{}"));
        mvc.perform(createRequest().header("Authorization", "Bearer " + jwt))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/reservation-processes").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + jwt).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(processes.count()).isZero();
        assertThat(HOLD_CALLS.get()).isZero();
    }

    @Test
    void anInvalidSlotOrDisabledCategoryNeverStartsAnExternalHold() throws Exception {
        mvc.perform(post("/api/reservation-processes").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + jwt).content("""
                        {"professionalId":15,"date":"%s","startTime":"09:45"}
                        """.formatted(date)))
                .andExpect(status().isConflict());
        CATALOG_REPLY.set(new Reply(200, """
                {"id":15,"enabled":true,"category":{"enabled":false},"weeklySchedules":[]}
                """));
        mvc.perform(createRequest().header("Authorization", "Bearer " + jwt))
                .andExpect(status().isConflict());
        assertThat(processes.count()).isZero();
        assertThat(HOLD_CALLS.get()).isZero();
    }

    @Test
    void invalidDatesAndNonMinuteTimesNeverReachRemoteServices() throws Exception {
        mvc.perform(post("/api/reservation-processes").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + jwt).content("""
                        {"professionalId":15,"date":"%s","startTime":"09:30:15"}
                        """.formatted(date)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SLOT"));
        mvc.perform(post("/api/reservation-processes").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + jwt).content("""
                        {"professionalId":15,"date":"%s","startTime":"09:30:00.000000001"}
                        """.formatted(date)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SLOT"));
        mvc.perform(post("/api/reservation-processes").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + jwt).content("""
                        {"professionalId":15,"date":"%s","startTime":"09:30"}
                        """.formatted(date.minusDays(8))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_SLOT"));
        assertThat(HOLD_CALLS.get()).isZero();
        assertThat(processes.count()).isZero();
    }

    @Test
    void catalogNotFoundOrMalformedDataAreTranslatedWithoutCreatingAnIntent() throws Exception {
        CATALOG_REPLY.set(new Reply(404, "{}"));
        mvc.perform(createRequest().header("Authorization", "Bearer " + jwt))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFESSIONAL_NOT_FOUND"));
        CATALOG_REPLY.set(new Reply(200, "{}"));
        mvc.perform(createRequest().header("Authorization", "Bearer " + jwt))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("INVALID_RESPONSE"));
        assertThat(processes.count()).isZero();
        assertThat(HOLD_CALLS.get()).isZero();
    }

    @Test
    void centralValidationErrorsAreMappedByStableCode() throws Exception {
        HOLD_REPLY.set(new Reply(400, "{\"code\":\"INVALID_SLOT\"}"));
        mvc.perform(createRequest().header("Authorization", "Bearer " + jwt))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"));
        processes.deleteAll();
        HOLD_REPLY.set(new Reply(400, "{\"code\":\"VALIDATION_ERROR\"}"));
        mvc.perform(createRequest().header("Authorization", "Bearer " + jwt))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("INVALID_RESPONSE"));
        assertThat(HOLD_CALLS.get()).isEqualTo(2);
    }

    private String createHeldProcess() throws Exception {
        var response = mvc.perform(createRequest().header("Authorization", "Bearer " + jwt))
                .andExpect(status().isCreated()).andReturn().getResponse();
        return JsonPath.read(response.getContentAsString(), "$.id");
    }

    private String registerAndLogin(String username) throws Exception {
        mvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"login":"%s","password":"clave-correcta","firstName":"Otra",
                "lastName":"Persona","email":"%s@example.com"}
                """.formatted(username, username))).andExpect(status().isCreated());
        var response = mvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON).content("""
                {"username":"%s","password":"clave-correcta"}
                """.formatted(username))).andExpect(status().isOk()).andReturn().getResponse();
        return JsonPath.read(response.getContentAsString(), "$.id_token");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder confirmRequest(String processId) {
        return post("/api/reservation-processes/{processId}/confirm", processId);
    }

    @Test
    void unauthenticatedRequestsDoNotConsultAnySource() throws Exception {
        mvc.perform(createRequest()).andExpect(status().isUnauthorized());
        assertThat(CATALOG_AUTH.get()).isNull();
        assertThat(HOLD_CALLS.get()).isZero();
        assertThat(processes.count()).isZero();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createRequest() {
        return post("/api/reservation-processes").contentType(MediaType.APPLICATION_JSON).content("""
                {"professionalId":15,"date":"%s","startTime":"09:30"}
                """.formatted(date));
    }

    private String emptyOccupancies() {
        return """
                {"professionalId":15,"from":"%s","to":"%s","occupancies":[]}
                """.formatted(date, date);
    }

    private static HttpServer server() {
        try {
            return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo iniciar servidor simulado", exception);
        }
    }

    private static void send(HttpExchange request, Reply reply) throws IOException {
        byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
        request.getResponseHeaders().set("Content-Type", "application/json");
        request.sendResponseHeaders(reply.status(), body.length);
        try (var output = request.getResponseBody()) {
            output.write(body);
        }
    }

    private record Reply(int status, String body) { }
}

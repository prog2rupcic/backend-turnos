package ar.edu.turnos.turnos.reservation.infrastructure.persistence.entity;

import ar.edu.turnos.turnos.reservation.domain.model.ReservationProcess;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "reservation_process")
public class ReservationProcessEntity {
    @Id
    private String id;
    @Column(name = "owner_user_id", nullable = false)
    private long ownerUserId;
    @Column(name = "professional_id", nullable = false)
    private long professionalId;
    @Column(name = "appointment_date", nullable = false)
    private LocalDate date;
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReservationProcess.Status status;
    @Column(name = "hold_id", length = 100)
    private String holdId;
    @Column(name = "external_process_id", length = 100)
    private String reservationProcessId;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "request_event_id", length = 100)
    private String requestEventId;
    @Column(name = "request_required_field", length = 32)
    private String requestRequiredField;
    @Column(name = "request_message", length = 2000)
    private String requestMessage;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(nullable = false)
    private Long version;

    protected ReservationProcessEntity() { }

    public static ReservationProcessEntity from(ReservationProcess process) {
        var entity = new ReservationProcessEntity();
        entity.id = process.id();
        entity.ownerUserId = process.ownerUserId();
        entity.professionalId = process.professionalId();
        entity.date = process.date();
        entity.startTime = process.startTime();
        entity.status = process.status();
        entity.holdId = process.holdId();
        entity.reservationProcessId = process.reservationProcessId();
        entity.expiresAt = process.expiresAt();
        entity.requestEventId = process.requestEventId();
        entity.requestRequiredField = process.requestRequiredField();
        entity.requestMessage = process.requestMessage();
        entity.createdAt = process.createdAt();
        entity.updatedAt = process.updatedAt();
        entity.version = process.version();
        return entity;
    }

    public String getId() { return id; }
    public long getOwnerUserId() { return ownerUserId; }
    public long getProfessionalId() { return professionalId; }
    public LocalDate getDate() { return date; }
    public LocalTime getStartTime() { return startTime; }
    public ReservationProcess.Status getStatus() { return status; }
    public String getHoldId() { return holdId; }
    public String getReservationProcessId() { return reservationProcessId; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getRequestEventId() { return requestEventId; }
    public String getRequestRequiredField() { return requestRequiredField; }
    public String getRequestMessage() { return requestMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}

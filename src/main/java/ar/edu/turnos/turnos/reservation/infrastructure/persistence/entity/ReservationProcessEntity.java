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
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(nullable = false)
    private Long version;

    protected ReservationProcessEntity() { }

    public ReservationProcessEntity(String id, long ownerUserId, long professionalId, LocalDate date,
                                    LocalTime startTime, ReservationProcess.Status status, String holdId,
                                    String reservationProcessId, Instant expiresAt, Instant createdAt,
                                    Instant updatedAt, Long version) {
        this.id = id;
        this.ownerUserId = ownerUserId;
        this.professionalId = professionalId;
        this.date = date;
        this.startTime = startTime;
        this.status = status;
        this.holdId = holdId;
        this.reservationProcessId = reservationProcessId;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
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
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}

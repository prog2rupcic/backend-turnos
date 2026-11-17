package ar.edu.turnos.turnos.reservation.domain.model;

import java.time.Instant;

public record PhoneSubmission(String eventId, String processId, String groupId, String reservationProcessId,
                              String holdId, String requestEventId, String phoneNumber, Instant occurredAt,
                              Instant expiresAt, DeliveryStatus deliveryStatus, int attempts) {
    public enum DeliveryStatus { PENDING, IN_FLIGHT, PUBLISHED, RETRY_EXHAUSTED, ABANDONED, REJECTED, COMPLETED }

    // Never include the phone in diagnostic output.
    @Override
    public String toString() {
        return "PhoneSubmission[eventId=" + eventId + ", deliveryStatus=" + deliveryStatus + "]";
    }

    public static String normalize(String phone) {
        if (phone == null || phone.length() > 100) {
            throw new IllegalArgumentException("Teléfono inválido");
        }
        String normalized = phone.replaceAll("[ \\-().]", "");
        if (!normalized.matches("\\+?[0-9]{7,15}")) {
            throw new IllegalArgumentException("Teléfono inválido");
        }
        return normalized;
    }
}

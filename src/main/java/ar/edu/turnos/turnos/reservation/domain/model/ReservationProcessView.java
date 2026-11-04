package ar.edu.turnos.turnos.reservation.domain.model;

public record ReservationProcessView(ReservationProcess process, boolean canSubmitPhone,
                                     PhoneSubmission.DeliveryStatus deliveryStatus) { }

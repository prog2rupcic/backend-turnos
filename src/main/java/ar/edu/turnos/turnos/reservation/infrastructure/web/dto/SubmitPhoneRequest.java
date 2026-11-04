package ar.edu.turnos.turnos.reservation.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitPhoneRequest(@NotBlank @Size(max = 100) String phoneNumber) {
    @Override
    public String toString() { return "SubmitPhoneRequest[phoneNumber=<redacted>]"; }
}

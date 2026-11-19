package ar.edu.turnos.turnos.user.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthenticationResponse(@JsonProperty("id_token") String idToken) {
}

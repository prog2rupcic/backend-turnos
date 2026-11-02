package ar.edu.turnos.turnos.availability.domain.ports.out;

import ar.edu.turnos.turnos.availability.domain.model.ProfessionalAgenda;

public interface ProfessionalAgendaSource {
    ProfessionalAgenda get(long professionalId);
}

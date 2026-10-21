package com.assistantrh.assistant_rh_api.dto;

import java.time.LocalDate;
import java.util.List;

public record ProfilAgentPersonnelDto(
        String matricule,
        String nom,
        String prenom,
        String email,
        LocalDate dateEmbauche,
        String typeEmploi,
        String categorie,
        String corps,
        String poste,
        String service,
        String direction,
        List<EtapeCarriere> parcoursCarriere
) {
    public ProfilAgentPersonnelDto {
        parcoursCarriere = List.copyOf(parcoursCarriere);
    }

    public record EtapeCarriere(
            LocalDate dateDebut,
            LocalDate dateFin,
            String categorie,
            String classe,
            Integer echelon
    ) {
    }
}

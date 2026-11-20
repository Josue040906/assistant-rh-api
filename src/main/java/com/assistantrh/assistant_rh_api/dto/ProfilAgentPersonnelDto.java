package com.assistantrh.assistant_rh_api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
        List<EtapeCarriere> parcoursCarriere,
        List<Diplome> diplomes,
        Map<String, Object> competencesEtFormations
) {
    public ProfilAgentPersonnelDto {
        parcoursCarriere = List.copyOf(parcoursCarriere);
        diplomes = diplomes == null ? List.of() : List.copyOf(diplomes);
    }

    /** Un diplôme de l'agent, sans identifiant interne. */
    public record Diplome(
            String niveau,
            String domaine,
            String etablissement,
            LocalDate dateObtention,
            String mention,
            Boolean principal
    ) {
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

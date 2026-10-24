package com.assistantrh.assistant_rh_api.dto;

import java.time.LocalDate;
import java.util.List;

public record AssistantAgentProfileDto(
        String nom,
        String prenom,
        String matricule,
        Integer age,
        String sexe,
        LocalDate dateEmbauche,
        String typeEmploi,
        String categorie,
        String corps,
        String poste,
        String service,
        String direction,
        List<Diplome> diplomes,
        List<EtapeCarriere> parcoursCarriere,
        List<Affectation> affectations,
        boolean competencesDisponibles,
        List<String> competences
) {
    public AssistantAgentProfileDto {
        diplomes = List.copyOf(diplomes);
        parcoursCarriere = List.copyOf(parcoursCarriere);
        affectations = List.copyOf(affectations);
        competences = List.copyOf(competences);
    }

    public record Diplome(
            String intitule,
            String domaine,
            String mention,
            String etablissement,
            LocalDate dateObtention
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

    public record Affectation(
            String poste,
            String service,
            String direction,
            LocalDate dateDebut,
            LocalDate dateFin
    ) {
    }
}

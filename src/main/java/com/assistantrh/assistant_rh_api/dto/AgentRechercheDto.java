package com.assistantrh.assistant_rh_api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public record AgentRechercheDto(
        String matricule,
        String nom,
        String prenom,
        String sexe,
        Integer age,
        @JsonProperty("date_embauche")
        LocalDate dateEmbauche,
        String poste,
        @JsonProperty("code_service")
        String codeService,
        String service,
        String direction,
        String categorie,
        String corps,
        String typeEmploi,
        String classe,
        Integer echelon,
        @JsonProperty("score_correspondance")
        Double scoreCorrespondance
) {
}

package com.assistantrh.assistant_rh_api.model;

import java.time.LocalDate;
import java.util.Objects;

public record AgentDocumentInfo(
        String nom,
        String prenom,
        String matricule,
        String service,
        String direction,
        String poste,
        String categorie,
        String corps,
        String classe,
        Integer echelon,
        LocalDate dateEmbauche,
        String lieuTravail,
        Integer directionId,
        Integer serviceId,
        Integer posteId
) {
    public AgentDocumentInfo(
            String nom,
            String prenom,
            String matricule,
            String service,
            String direction,
            String poste,
            String categorie,
            String corps,
            String classe,
            Integer echelon,
            LocalDate dateEmbauche
    ) {
        this(
                nom,
                prenom,
                matricule,
                service,
                direction,
                poste,
                categorie,
                corps,
                classe,
                echelon,
                dateEmbauche,
                null,
                null,
                null,
                null
        );
    }

    public AgentDocumentInfo(
            String nom,
            String prenom,
            String matricule,
            String service,
            String direction,
            String poste,
            String categorie,
            String corps,
            String classe,
            Integer echelon,
            LocalDate dateEmbauche,
            String lieuTravail
    ) {
        this(
                nom,
                prenom,
                matricule,
                service,
                direction,
                poste,
                categorie,
                corps,
                classe,
                echelon,
                dateEmbauche,
                lieuTravail,
                null,
                null,
                null
        );
    }

    public AgentDocumentInfo {
        Objects.requireNonNull(nom, "Le nom de l'agent est obligatoire.");
        Objects.requireNonNull(prenom, "Le prénom de l'agent est obligatoire.");
        Objects.requireNonNull(matricule, "Le matricule de l'agent est obligatoire.");

        if (nom.isBlank() || prenom.isBlank() || matricule.isBlank()) {
            throw new IllegalArgumentException(
                    "Le nom, le prénom et le matricule de l'agent ne peuvent pas etre vides."
            );
        }
    }

    public String nomComplet() {
        return prenom + " " + nom;
    }
}

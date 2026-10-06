package com.assistantrh.assistant_rh_api.model;

import java.util.Objects;

public record AgentDocumentInfo(
        String nomComplet,
        String matricule,
        String service,
        String poste
) {
    public AgentDocumentInfo {
        Objects.requireNonNull(nomComplet, "Le nom de l'agent est obligatoire.");
        Objects.requireNonNull(matricule, "Le matricule de l'agent est obligatoire.");

        if (nomComplet.isBlank() || matricule.isBlank()) {
            throw new IllegalArgumentException(
                    "Le nom et le matricule de l'agent ne peuvent pas etre vides."
            );
        }
    }
}

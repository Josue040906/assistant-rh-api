package com.assistantrh.assistant_rh_api.model;

import java.time.LocalDateTime;
import java.util.Objects;

public record Activite(
        Long id,
        Integer acteurId,
        Integer employeId,
        String typeAction,
        String description,
        LocalDateTime dateHeure,
        String acteurEmail,
        String employeMatricule,
        String employeNom,
        String employePrenom
) {
    public Activite {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'activité doit être positif."
            );
        }
        Objects.requireNonNull(typeAction, "Le type d'action est obligatoire.");
        Objects.requireNonNull(description, "La description est obligatoire.");
        Objects.requireNonNull(dateHeure, "La date de l'activité est obligatoire.");
    }
}

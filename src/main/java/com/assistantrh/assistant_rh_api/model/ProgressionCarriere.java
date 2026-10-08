package com.assistantrh.assistant_rh_api.model;

import java.util.Objects;

public record ProgressionCarriere(
        SituationCarriereSnapshot situationActuelle,
        SituationCarriereSnapshot situationDemandee,
        boolean eligibiliteDeterminee,
        Boolean eligible,
        String typeEvolution
) {
    public ProgressionCarriere {
        Objects.requireNonNull(
                situationActuelle,
                "La situation actuelle est obligatoire."
        );
        Objects.requireNonNull(typeEvolution, "Le type d'évolution est obligatoire.");
    }
}

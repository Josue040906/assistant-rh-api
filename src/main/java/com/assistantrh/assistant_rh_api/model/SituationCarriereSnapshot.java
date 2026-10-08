package com.assistantrh.assistant_rh_api.model;

import java.time.LocalDate;
import java.util.Objects;

public record SituationCarriereSnapshot(
        Integer echelonId,
        Integer echelonOrdre,
        Integer classeId,
        String classeLibelle,
        Integer classeOrdre,
        Integer dureeMinAnnees,
        LocalDate dateDebut
) {
    public SituationCarriereSnapshot {
        Objects.requireNonNull(echelonId, "L'échelon de carrière est obligatoire.");
        Objects.requireNonNull(echelonOrdre, "L'ordre de l'échelon est obligatoire.");
        Objects.requireNonNull(classeId, "La classe de carrière est obligatoire.");
        Objects.requireNonNull(classeLibelle, "Le libellé de la classe est obligatoire.");
        Objects.requireNonNull(classeOrdre, "L'ordre de la classe est obligatoire.");
    }
}

package com.assistantrh.assistant_rh_api.model;

import java.time.LocalDate;
import java.util.Objects;

public record TypeDocument(
        Integer id,
        String code,
        String libelle,
        String description,
        LocalDate dateDebutValidite,
        LocalDate dateFinValidite
) {
    public boolean estActifA(LocalDate date) {
        Objects.requireNonNull(date, "La date de référence est obligatoire.");

        return (dateDebutValidite == null || !date.isBefore(dateDebutValidite))
                && (dateFinValidite == null || !date.isAfter(dateFinValidite));
    }
}

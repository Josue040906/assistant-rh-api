package com.assistantrh.assistant_rh_api.model;

import java.time.LocalDate;
import java.util.Objects;

public record TypeDocument(
        Integer id,
        String code,
        String libelle,
        String description,
        LocalDate dateDebutValidite,
        LocalDate dateFinValidite,
        // Le décideur auquel la demande s'adresse par défaut : un poste et sa fonction.
        Integer destinatairePosteId,
        String destinataireFonction,
        // Son titulaire actuel, pour information (la lettre nomme la fonction).
        String destinataireTitulaire
) {
    public TypeDocument(Integer id, String code, String libelle, String description,
                        LocalDate dateDebutValidite, LocalDate dateFinValidite) {
        this(id, code, libelle, description, dateDebutValidite, dateFinValidite, null, null, null);
    }

    public boolean estActifA(LocalDate date) {
        Objects.requireNonNull(date, "La date de référence est obligatoire.");

        return (dateDebutValidite == null || !date.isBefore(dateDebutValidite))
                && (dateFinValidite == null || !date.isAfter(dateFinValidite));
    }
}

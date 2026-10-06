package com.assistantrh.assistant_rh_api.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeDocumentTests {

    @Test
    void estActifPendantSesDatesDeValiditeIncluses() {
        TypeDocument type = new TypeDocument(
                1,
                "CONGE",
                "Demande de congé",
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31)
        );

        assertTrue(type.estActifA(LocalDate.of(2026, 1, 1)));
        assertTrue(type.estActifA(LocalDate.of(2026, 12, 31)));
        assertFalse(type.estActifA(LocalDate.of(2025, 12, 31)));
        assertFalse(type.estActifA(LocalDate.of(2027, 1, 1)));
    }

    @Test
    void accepteUnePeriodeDeValiditeSansLimiteDeDebutOuDeFin() {
        TypeDocument type = new TypeDocument(
                1,
                "CONGE",
                "Demande de congé",
                null,
                null,
                null
        );

        assertTrue(type.estActifA(LocalDate.now()));
    }
}

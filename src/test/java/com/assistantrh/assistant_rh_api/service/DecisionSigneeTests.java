package com.assistantrh.assistant_rh_api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecisionSigneeTests {

    @Test
    void leSignataireEstObligatoireLeNumeroFacultatif() {
        assertEquals("Décision Arrêté n° 12/2026, signée par Le Directeur",
                DecisionSignee.texte(" Arrêté n° 12/2026 ", "Le Directeur"));
        assertEquals("Décision signée par Le Directeur", DecisionSignee.texte(null, "Le Directeur"));
        assertEquals("Décision signée par Le Directeur", DecisionSignee.texte("  ", "Le Directeur"));
        assertThrows(IllegalArgumentException.class, () -> DecisionSignee.texte("Arrêté n° 12/2026", " "));
    }
}

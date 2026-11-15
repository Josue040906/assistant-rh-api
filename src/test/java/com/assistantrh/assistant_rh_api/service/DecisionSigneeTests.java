package com.assistantrh.assistant_rh_api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecisionSigneeTests {

    @Test
    void leSignataireEstObligatoire() {
        assertEquals("Décision signée par Le Directeur", DecisionSignee.texte(" Le Directeur "));
        assertThrows(IllegalArgumentException.class, () -> DecisionSignee.texte(" "));
        assertThrows(IllegalArgumentException.class, () -> DecisionSignee.texte(null));
    }
}

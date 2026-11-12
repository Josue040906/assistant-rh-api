package com.assistantrh.assistant_rh_api.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GradeParIndiceTests {
    private final List<Map<String, Object>> grades = List.of(
            Map.of("code", "3C1E", "indice", 350),
            Map.of("code", "3C2E", "indice", 370),
            Map.of("code", "3C3E", "indice", 390),
            Map.of("code", "2C1E", "indice", 410));

    private String propose(Integer indice) {
        return GradeParIndice.retenu(grades, indice).map(g -> g.get("code").toString()).orElse(null);
    }

    @Test
    void unIndiceExistantDonneCeGradeSinonLePremierSuperieur() {
        assertEquals("3C3E", propose(390));
        assertEquals("3C2E", propose(360));
        assertEquals("3C1E", propose(300));
    }

    @Test
    void jamaisUnIndicePlusLoinQueLePremierSuperieur() {
        assertEquals("3C2E", propose(351));
        assertEquals("2C1E", propose(391));
    }

    @Test
    void auDelaDeLaGrilleLePlusHautGradeEstPropose() {
        assertEquals("2C1E", propose(999));
        assertEquals("3C1E", propose(null));
    }
}

package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SituationCarriereServiceTests {

    private final SituationCarriereRepository repository =
            mock(SituationCarriereRepository.class);
    private final SituationCarriereService service =
            new SituationCarriereService(repository);

    @Test
    void analyseUnAvancementVersLeProchainEchelon() {
        when(repository.findSituationActuelle(7))
                .thenReturn(Optional.of(situationActuelle(2, 1)));
        when(repository.findHistorique(7)).thenReturn(List.of());
        when(repository.findEchelonSuivant(3, 1))
                .thenReturn(Optional.of(Map.of(
                        "id", 12,
                        "classe_id", 3,
                        "ordre", 2,
                        "duree_min", 3,
                        "classe_libelle", "1re classe",
                        "classe_ordre", 2
                )));

        Map<String, Object> analyse =
                service.analyserEvolutionCarriere(7).orElseThrow();

        assertEquals("ECHELON", ((Map<?, ?>) analyse.get("evolution")).get("type"));
        assertEquals(12, ((Map<?, ?>) analyse.get("situationSuivante")).get("echelonId"));
        assertEquals(2L, ((Map<?, ?>) analyse.get("ancienneteActuelle")).get("annees"));
        assertEquals(4L, ((Map<?, ?>) analyse.get("ancienneteActuelle")).get("mois"));
        assertEquals(28L, ((Map<?, ?>) analyse.get("ancienneteActuelle")).get("moisTotal"));
        assertEquals(Boolean.TRUE, analyse.get("eligible"));
    }

    @Test
    void analyseLeChangementDeClasseQuandIlNExistePasDeProchainEchelon() {
        when(repository.findSituationActuelle(7))
                .thenReturn(Optional.of(situationActuelle(2, 3)));
        when(repository.findHistorique(7)).thenReturn(List.of());
        when(repository.findEchelonSuivant(3, 3))
                .thenReturn(Optional.empty());
        when(repository.findClasseSuivante(2))
                .thenReturn(Optional.of(Map.of(
                        "id", 4,
                        "libelle", "Principale",
                        "ordre", 3
                )));
        when(repository.findPremierEchelon(4))
                .thenReturn(Optional.of(Map.of(
                        "id", 20,
                        "classe_id", 4,
                        "ordre", 1,
                        "duree_min", 2,
                        "classe_libelle", "Principale",
                        "classe_ordre", 3
                )));

        Map<String, Object> analyse =
                service.analyserEvolutionCarriere(7).orElseThrow();

        assertEquals("CLASSE", ((Map<?, ?>) analyse.get("evolution")).get("type"));
        assertEquals("Principale", ((Map<?, ?>) analyse.get("situationSuivante")).get("classeLibelle"));
        assertEquals(1, ((Map<?, ?>) analyse.get("situationSuivante")).get("echelonOrdre"));
    }

    @Test
    void retourneAucuneEvolutionEtEligibiliteIndetermineeSansDureeMinimum() {
        Map<String, Object> situation = situationActuelle(null, 3);
        when(repository.findSituationActuelle(7)).thenReturn(Optional.of(situation));
        when(repository.findHistorique(7)).thenReturn(List.of());
        when(repository.findEchelonSuivant(3, 3)).thenReturn(Optional.empty());
        when(repository.findClasseSuivante(2)).thenReturn(Optional.empty());

        Map<String, Object> analyse =
                service.analyserEvolutionCarriere(7).orElseThrow();

        assertEquals("AUCUNE", ((Map<?, ?>) analyse.get("evolution")).get("type"));
        assertNull(analyse.get("eligible"));
        assertFalse((Boolean) analyse.get("eligibiliteDeterminee"));
    }

    @Test
    void signaleUneSituationActuelleAbsente() {
        when(repository.findSituationActuelle(7)).thenReturn(Optional.empty());

        assertTrue(service.analyserEvolutionCarriere(7).isEmpty());
    }

    private Map<String, Object> situationActuelle(
            Integer dureeMin,
            Integer echelonOrdre
    ) {
        Map<String, Object> situation = new HashMap<>();
        situation.put("historique_id", 31);
        situation.put("employe_id", 7);
        situation.put("date_debut", LocalDate.now().minusMonths(28));
        situation.put("echelon_id", 11);
        situation.put("echelon_ordre", echelonOrdre);
        situation.put("duree_min", dureeMin);
        situation.put("classe_id", 3);
        situation.put("classe_libelle", "1re classe");
        situation.put("classe_ordre", 2);
        return situation;
    }
}

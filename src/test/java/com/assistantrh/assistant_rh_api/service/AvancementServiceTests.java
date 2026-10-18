package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvancementServiceTests {

    private final SituationCarriereRepository situationRepository =
            mock(SituationCarriereRepository.class);
    private final EmployeRepository employeRepository =
            mock(EmployeRepository.class);
    private final UtilisateurRepository utilisateurRepository =
            mock(UtilisateurRepository.class);
    private final ActiviteService activiteService = mock(ActiviteService.class);
    private final AvancementService service = new AvancementService(
            situationRepository,
            employeRepository,
            utilisateurRepository,
            activiteService
    );

    @Test
    void refuseAvancementQuandDureeNonRenseignee() {
        when(utilisateurRepository.estAgentSpersActif(4)).thenReturn(true);
        when(situationRepository.findSituationActuelle(9))
                .thenReturn(Optional.of(situationActuelle(null)));
        when(situationRepository.findEchelonSuivant(2, 1))
                .thenReturn(Optional.of(Map.of(
                        "id", 15, "classe_id", 2, "ordre", 2
                )));

        Map<String, Object> analyse = service.analyser(9, 4).orElseThrow();

        assertFalse((Boolean) analyse.get("eligible"));
        assertEquals(1, ((java.util.List<?>) analyse.get("motifs")).size());
        verify(situationRepository, never()).fermerSituation(any(), any());
    }

    @Test
    void refuseAvancementSansEchelonSuivantMemeClasse() {
        when(utilisateurRepository.estAgentSpersActif(4)).thenReturn(true);
        when(situationRepository.findSituationActuelle(9))
                .thenReturn(Optional.of(situationActuelle(2)));
        when(situationRepository.findEchelonSuivant(2, 1))
                .thenReturn(Optional.empty());

        Map<String, Object> analyse = service.analyser(9, 4).orElseThrow();

        assertFalse((Boolean) analyse.get("eligible"));
        assertTrue(((java.util.List<?>) analyse.get("motifs")).get(0).toString()
                .contains("Aucun échelon suivant"));
    }

    @Test
    void confirmeEtJournaliseUnAvancementEligible() {
        LocalDate today = LocalDate.now();
        Map<String, Object> current = situationActuelle(2);
        Map<String, Object> updated = new HashMap<>(current);
        updated.put("historique_id", 22);
        when(utilisateurRepository.estAgentSpersActif(4)).thenReturn(true);
        when(situationRepository.verrouillerEmploye(9)).thenReturn(true);
        when(situationRepository.findSituationActuelle(9))
                .thenReturn(Optional.of(current), Optional.of(updated));
        when(situationRepository.findEchelonSuivant(2, 1))
                .thenReturn(Optional.of(Map.of(
                        "id", 15,
                        "classe_id", 2,
                        "ordre", 2,
                        "classe_libelle", "Classe II"
                )));
        when(employeRepository.findById(9))
                .thenReturn(Optional.of(Map.of("matricule", "AG-9")));
        when(situationRepository.fermerSituation(
                eq(21), eq(Date.valueOf(today.minusDays(1)))
        )).thenReturn(1);
        when(situationRepository.creerSituation(
                9, 3, 15, Date.valueOf(today)
        )).thenReturn(1);
        when(situationRepository.creerAvancement(
                9, 21, 22, Date.valueOf(today)
        )).thenReturn(1);

        Map<String, Object> response = service.confirmer(9, 4, today);

        assertEquals("Avancement d'échelon enregistré avec succès.", response.get("message"));
        verify(situationRepository).creerAvancement(
                9, 21, 22, Date.valueOf(today)
        );
        verify(activiteService).enregistrer(
                eq(4), eq(9), eq("MODIFICATION"), any()
        );
    }

    private Map<String, Object> situationActuelle(Integer dureeMin) {
        Map<String, Object> situation = new HashMap<>();
        situation.put("historique_id", 21);
        situation.put("categorie_id", 3);
        situation.put("categorie_code", "III");
        situation.put("classe_id", 2);
        situation.put("classe_libelle", "Classe II");
        situation.put("echelon_id", 14);
        situation.put("echelon_ordre", 1);
        situation.put("date_debut", LocalDate.now().minusYears(3));
        situation.put("duree_min", dureeMin);
        return situation;
    }
}

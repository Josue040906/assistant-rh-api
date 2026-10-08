package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.model.ProgressionCarriere;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SituationCarriereServiceTests {

    private final SituationCarriereRepository repository =
            mock(SituationCarriereRepository.class);
    private final EmployeRepository employeRepository =
            mock(EmployeRepository.class);
    private final UtilisateurRepository utilisateurRepository =
            mock(UtilisateurRepository.class);
    private final ActiviteService activiteService =
            mock(ActiviteService.class);
    private final SituationCarriereService service =
            new SituationCarriereService(
                    repository,
                    employeRepository,
                    utilisateurRepository,
                    activiteService
            );

    @Test
    void creeLaPremiereSituationEtJournaliseLaCreation() {
        LocalDate dateDebut = LocalDate.now().minusDays(1);
        when(utilisateurRepository.estAgentSpersActif(3)).thenReturn(true);
        when(repository.verrouillerEmploye(7)).thenReturn(true);
        when(employeRepository.findById(7))
                .thenReturn(Optional.of(Map.of("matricule", "AG-7")));
        when(repository.findEchelon(12))
                .thenReturn(Optional.of(Map.of(
                        "classe_libelle", "Principale",
                        "echelon_ordre", 2
                )));
        when(repository.findDerniereSituationOuverte(7))
                .thenReturn(Optional.empty());
        when(repository.creerSituation(
                7, 12, Date.valueOf(dateDebut)
        )).thenReturn(1);

        service.ajouterSituationCarriere(7, 3, 12, dateDebut);

        verify(repository).verrouillerEmploye(7);
        verify(repository).creerSituation(
                7, 12, Date.valueOf(dateDebut)
        );
        verify(activiteService).enregistrer(
                eq(3),
                eq(7),
                eq("CREATION"),
                contains("AG-7")
        );
    }

    @Test
    void fermeLaSituationPrecedenteAvantUneNouvelleSituation() {
        LocalDate debutActuel = LocalDate.now().minusYears(2);
        LocalDate nouveauDebut = LocalDate.now().minusYears(1);
        when(utilisateurRepository.estAgentSpersActif(3)).thenReturn(true);
        when(repository.verrouillerEmploye(7)).thenReturn(true);
        when(employeRepository.findById(7))
                .thenReturn(Optional.of(Map.of("matricule", "AG-7")));
        when(repository.findEchelon(12))
                .thenReturn(Optional.of(Map.of(
                        "classe_libelle", "Principale",
                        "echelon_ordre", 2
                )));
        when(repository.findDerniereSituationOuverte(7))
                .thenReturn(Optional.of(Map.of(
                        "historique_id", 31,
                        "date_debut", debutActuel
                )));
        when(repository.fermerSituation(
                31, Date.valueOf(nouveauDebut.minusDays(1))
        )).thenReturn(1);
        when(repository.creerSituation(
                7, 12, Date.valueOf(nouveauDebut)
        )).thenReturn(1);

        service.ajouterSituationCarriere(7, 3, 12, nouveauDebut);

        verify(repository).fermerSituation(
                31, Date.valueOf(nouveauDebut.minusDays(1))
        );
        verify(activiteService).enregistrer(
                eq(3),
                eq(7),
                eq("MODIFICATION"),
                contains("AG-7")
        );
    }

    @Test
    void refuseUneDateDImpactFutureSansEcrireNiJournaliser() {
        when(utilisateurRepository.estAgentSpersActif(3)).thenReturn(true);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.ajouterSituationCarriere(
                        7, 3, 12, LocalDate.now().plusDays(1)
                )
        );

        verify(repository, never()).creerSituation(
                eq(7), eq(12), org.mockito.ArgumentMatchers.any()
        );
        verify(activiteService, never()).enregistrer(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

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

    @Test
    void retourneUneProgressionTypeePourLaDemandeDavancement() {
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

        ProgressionCarriere progression =
                service.obtenirProgressionCarriere(7).orElseThrow();

        assertEquals(11, progression.situationActuelle().echelonId());
        assertEquals(1, progression.situationActuelle().echelonOrdre());
        assertEquals(12, progression.situationDemandee().echelonId());
        assertEquals("1re classe", progression.situationDemandee().classeLibelle());
        assertTrue(progression.eligibiliteDeterminee());
        assertEquals(Boolean.TRUE, progression.eligible());
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

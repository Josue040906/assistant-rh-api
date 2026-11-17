package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.AffectationRepository;
import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import com.assistantrh.assistant_rh_api.repository.RetraiteRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DemandeApplicabiliteServiceTests {
    private final SituationCarriereRepository situations = mock(SituationCarriereRepository.class);
    private final AffectationRepository affectations = mock(AffectationRepository.class);
    private final RetraiteRepository retraites = mock(RetraiteRepository.class);
    private final DocumentRhRepository documents = mock(DocumentRhRepository.class);
    private final AffectationService affectation = mock(AffectationService.class);
    private final CongeService conges = mock(CongeService.class);
    private final TitularisationService titularisation = mock(TitularisationService.class);
    private final DemandeApplicabiliteService service = new DemandeApplicabiliteService(
            situations, affectations, retraites, documents, affectation, conges, titularisation);

    private final LocalDate demain = LocalDate.now().plusDays(1);

    private void situation(int echelonId, LocalDate debut) {
        Map<String, Object> actuelle = new HashMap<>();
        actuelle.put("echelon_id", echelonId);
        actuelle.put("date_debut", debut);
        actuelle.put("grade_code", "2C1E");
        actuelle.put("grade_libelle", "2e classe, 1er échelon");
        when(situations.findSituationActuelle(42)).thenReturn(Optional.of(actuelle));
    }

    @Test
    void unAvancementDemandeAvantUnReclassementDevientCaduc() {
        situation(7, LocalDate.now());

        assertEquals(Optional.of("La situation de carrière de l'agent a changé depuis la demande"
                        + " (grade actuel : 2C1E — 2e classe, 1er échelon) : elle ne peut plus être appliquée."),
                service.empechement("AVANCEMENT", 42,
                        Map.of("situationActuelle", Map.of("echelonId", 4)), demain, 9L));
    }

    @Test
    void unAvancementDontLaSituationNAPasChangeResteApplicable() {
        situation(4, LocalDate.now().minusYears(3));

        assertEquals(Optional.empty(), service.empechement("AVANCEMENT", 42,
                Map.of("situationActuelle", Map.of("echelonId", 4)), demain, 9L));
    }

    @Test
    void unAgentPartiALaRetraiteNePeutPlusRienRecevoir() {
        when(retraites.dateRetraite(42)).thenReturn(Optional.of(LocalDate.of(2026, 11, 3)));

        assertEquals(Optional.of("L'agent est parti à la retraite le 03/11/2026."),
                service.empechement("MUTATION", 42, Map.of(), demain, 9L));
    }

    @Test
    void uneMutationDeLieuSeulGardeLePosteEtLeServiceActuels() {
        when(affectations.findActiveByEmployeId(42)).thenReturn(Map.of("poste_id", 3, "service_id", 2));
        Map<String, Object> cible = new HashMap<>();
        cible.put("posteId", null);
        cible.put("serviceId", null);
        cible.put("lieuTravail", "Toamasina");

        assertEquals(new DemandeApplicabiliteService.CibleMutation(3, 2, "Toamasina"),
                service.cibleMutation(42, Map.of("situationDemandee", cible)));
    }

    @Test
    void unNouveauServiceSansPosteNeSeResoutPas() {
        when(affectations.findActiveByEmployeId(42)).thenReturn(Map.of("poste_id", 3, "service_id", 2));
        Map<String, Object> cible = new HashMap<>();
        cible.put("serviceId", 8);

        assertEquals("Précisez le poste dans le nouveau service : une affectation exige un poste.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.cibleMutation(42, Map.of("situationDemandee", cible))).getMessage());
        assertEquals(Optional.of("Précisez le poste dans le nouveau service : une affectation exige un poste."),
                service.empechement("MUTATION", 42, Map.of("situationDemandee", cible), demain, null));
    }

    @Test
    void uneMutationEstVerifieeParLaMemeRegleQueLaMutationDirecte() {
        when(affectation.empechementMutation(42, 12, 5, null, demain))
                .thenReturn(Optional.of("L'agent occupe déjà ce poste dans ce service : rien ne changerait."));

        assertEquals(Optional.of("L'agent occupe déjà ce poste dans ce service : rien ne changerait."),
                service.empechement("MUTATION", 42,
                        Map.of("situationDemandee", Map.of("posteId", 12, "serviceId", 5)), demain, null));
        verify(affectation).empechementMutation(42, 12, 5, null, demain);
    }

    @Test
    void unCongeSansSoldeSuffisantEstEmpeche() {
        LocalDate debut = LocalDate.of(2026, 12, 1);
        when(conges.analyser(42, debut)).thenReturn(Map.of("solde", new BigDecimal("2.5")));

        assertEquals(Optional.of("Solde insuffisant : 2.5 jours disponibles au 01/12/2026, 5 demandés."),
                service.empechement("CONGE", 42,
                        Map.of("dateDebut", "2026-12-01", "dateFin", "2026-12-05"), debut, 9L));
    }

    @Test
    void unCongeQuiChevaucheUnAutreEstEmpecheSansSeCompterLuiMeme() {
        LocalDate debut = LocalDate.of(2026, 12, 1);
        when(documents.congeEnConflit(42, debut, LocalDate.of(2026, 12, 5), 9L))
                .thenReturn(Optional.of("du 02/12/2026 au 03/12/2026"));

        assertEquals(Optional.of("L'agent a déjà un congé du 02/12/2026 au 03/12/2026 sur ces dates."),
                service.empechement("CONGE", 42,
                        Map.of("dateDebut", "2026-12-01", "dateFin", "2026-12-05"), debut, 9L));
    }

    @Test
    void uneRetraiteNePeutPasPrecederLaDureeDeServiceExigee() {
        when(documents.dateRetraitePossible(42)).thenReturn(Optional.of(LocalDate.of(2027, 3, 1)));

        assertEquals(Optional.of("La durée de service exigée sera atteinte le 01/03/2027 : le départ ne peut pas la précéder."),
                service.empechement("RETRAITE", 42, Map.of(), LocalDate.of(2027, 1, 1), 9L));
    }

    @Test
    void uneTitularisationDUnAgentDejaFonctionnaireEstEmpechee() {
        situation(4, LocalDate.now().minusYears(1));
        when(titularisation.analyser(42)).thenReturn(Map.of("possible", false,
                "raison", "L'agent est déjà fonctionnaire."));

        assertEquals(Optional.of("L'agent est déjà fonctionnaire."), service.empechement("TITULARISATION", 42,
                Map.of("situationActuelle", Map.of("echelonId", 4)), demain, 9L));
    }
}

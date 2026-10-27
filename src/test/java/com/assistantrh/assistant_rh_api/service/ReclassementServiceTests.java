package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.DiplomeRepository;
import com.assistantrh.assistant_rh_api.repository.ReclassementRepository;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReclassementServiceTests {

    private final DiplomeRepository diplomeRepository = mock(DiplomeRepository.class);
    private final SituationCarriereRepository situationRepository =
            mock(SituationCarriereRepository.class);
    private final EmployeRepository employeRepository = mock(EmployeRepository.class);
    private final UtilisateurRepository utilisateurRepository =
            mock(UtilisateurRepository.class);
    private final ActiviteService activiteService = mock(ActiviteService.class);
    private final ReclassementRepository reclassementRepository = mock(ReclassementRepository.class);
    private final ReclassementService service = new ReclassementService(
            diplomeRepository,
            situationRepository,
            employeRepository,
            utilisateurRepository,
            activiteService,
            reclassementRepository
    );

    @BeforeEach
    void preparerHorodatageActivite() {
        when(activiteService.enregistrer(any(), any(), any(), any()))
                .thenReturn(101);
        when(activiteService.obtenirDateHeure(101))
                .thenReturn(LocalDateTime.of(LocalDate.now(), java.time.LocalTime.NOON));
        // Un enregistrement réussi est le comportement par défaut du dépôt mocké.
        // Les tests qui couvrent un échec peuvent remplacer explicitement ce retour.
        when(reclassementRepository.enregistrer(any(), any(), any()))
                .thenReturn(1);
    }

    @Test
    void reclasseAvecUnDiplomeEtPreserveLaPeriodeDeCarriere() {
        LocalDate debut = LocalDate.now().minusYears(2);
        preparer(true, 3, 4, List.of(diplome(18, 7, 5,
                LocalDate.now().minusYears(1), 2, 3)), situation(31, 3, debut));
        when(employeRepository.updateCategorie(9, 3, 2)).thenReturn(1);
        when(situationRepository.fermerSituation(
                31, Date.valueOf(LocalDate.now().minusDays(1))
        )).thenReturn(1);
        when(situationRepository.creerSituation(
                9, 2, 44, Date.valueOf(LocalDate.now())
        )).thenReturn(1);

        Map<String, Object> response = service.confirmer(9, 4, null);

        assertEquals("RECLASSIFIED", response.get("resultCode"));
        assertEquals(Boolean.TRUE, response.get("changed"));
        verify(employeRepository).updateCategorie(9, 3, 2);
        verify(situationRepository).fermerSituation(
                31, Date.valueOf(LocalDate.now().minusDays(1))
        );
        verify(situationRepository).creerSituation(
                9, 2, 44, Date.valueOf(LocalDate.now())
        );
        verify(activiteService).enregistrer(
                eq(4), eq(9), eq("MODIFICATION"),
                contains("diplôme agent #18")
        );
        verify(reclassementRepository).enregistrer(9, 101, 2);
    }

    @Test
    void reclasseLaCategorieMemeSansRegleDeClasseEtEchelon() {
        preparer(true, 3, 4, List.of(diplome(18, 7, 5,
                LocalDate.now().minusYears(1), 2, 3)), null);
        when(employeRepository.updateCategorie(9, 3, 2)).thenReturn(1);

        Map<String, Object> response = service.confirmer(9, 4, null);

        assertEquals("RECLASSIFIED", response.get("resultCode"));
        assertEquals(Boolean.FALSE, response.get("echelonModifie"));
        verify(employeRepository).updateCategorie(9, 3, 2);
        verify(situationRepository, never()).creerSituation(any(), any(), any(), any());
    }

    @Test
    void neModifiePasLechelonLorsqueLaCategorieEstDejaIdentique() {
        LocalDate debut = LocalDate.now();
        Map<String, Object> situation = situation(31, 2, debut);
        situation.put("echelon_id", 43);
        preparer(true, 2, 3, List.of(diplome(18, 7, 5,
                debut, 2, 3)), situation);
        Map<String, Object> response = service.confirmer(9, 4, debut);

        assertEquals("ALREADY_CLASSIFIED", response.get("resultCode"));
        verify(situationRepository, never()).modifierCategorieSituation(any(), any());
        verify(employeRepository, never()).updateCategorie(any(), any(), any());
    }

    @Test
    void privilegieLeNiveauLePlusEleveSurLaDateDuDiplome() {
        preparer(false, 3, 4, List.of(
                diplome(18, 7, 5, LocalDate.now().minusYears(8), 2, 3),
                diplome(29, 8, 2, LocalDate.now().minusMonths(1), 5, 6)
        ), null);

        Map<String, Object> proposal = service.proposer(9, 4);
        Map<?, ?> retenu = (Map<?, ?>) proposal.get("diplomeRetenu");

        assertEquals(7, ((Number) retenu.get("niveauId")).intValue());
        assertEquals(5, ((Number) retenu.get("ordreAcademique")).intValue());
    }

    @Test
    void departageLesDiplomesDeMemeNiveauParDatePuisParAssociation() {
        LocalDate ancienne = LocalDate.now().minusYears(4);
        LocalDate recente = LocalDate.now().minusYears(1);
        preparer(false, 3, 4, List.of(
                diplome(18, 7, 5, recente, 2, 3),
                diplome(19, 8, 5, ancienne, 5, 6),
                diplome(30, 9, 5, recente, 6, 7)
        ), null);

        Map<?, ?> retenu = (Map<?, ?>) service.proposer(9, 4)
                .get("diplomeRetenu");

        assertEquals(9, ((Number) retenu.get("niveauId")).intValue());
        assertEquals(30, ((Number) retenu.get("employeDiplomeId")).intValue());
    }

    @Test
    void prefereUneDateRenseigneeAuxDatesNullesAuMemeNiveau() {
        preparer(false, 3, 4, List.of(
                diplome(31, 10, 5, null, 2, 3),
                diplome(30, 9, 5, LocalDate.now().minusYears(3), 6, 7)
        ), null);

        Map<?, ?> retenu = (Map<?, ?>) service.proposer(9, 4)
                .get("diplomeRetenu");

        assertEquals(9, ((Number) retenu.get("niveauId")).intValue());
    }

    @Test
    void retourneUnNoOpSansEcritureQuandCategorieEtCorpsSontDejaCibles() {
        preparer(true, 2, 3, List.of(diplome(18, 7, 5,
                LocalDate.now().minusYears(1), 2, 3)), null);

        Map<String, Object> response = service.confirmer(9, 4, null);

        assertEquals("ALREADY_CLASSIFIED", response.get("resultCode"));
        assertEquals(Boolean.FALSE, response.get("changed"));
        assertTrue(response.get("message").toString().contains("Aucun reclassement"));
        verify(employeRepository, never()).updateCategorie(any(), any(), any());
        verify(situationRepository, never()).fermerSituation(any(), any());
        verify(activiteService, never()).enregistrer(any(), any(), any(), any());
        verify(reclassementRepository, never()).enregistrer(any(), any(), any());
    }

    @Test
    void reclassifieLaCategorieSansEchelonFiableSansReecrireLHistorique() {
        LocalDate debut = LocalDate.now().minusYears(1);
        Map<String, Object> situation = situation(31, 3, debut);
        situation.put("echelon_id", null);
        preparer(true, 3, 4, List.of(diplome(18, 7, 5,
                LocalDate.now().minusYears(1), 2, 3)), situation);
        when(employeRepository.updateCategorie(9, 3, 2)).thenReturn(1);

        Map<String, Object> response = service.confirmer(9, 4, null);

        assertEquals("RECLASSIFIED", response.get("resultCode"));
        assertEquals(Boolean.FALSE, response.get("echelonModifie"));
        verify(employeRepository).updateCategorie(9, 3, 2);
        verify(situationRepository, never()).modifierCategorieSituation(any(), any());
        verify(situationRepository, never()).fermerSituation(any(), any());
        verify(situationRepository, never()).creerSituation(any(), any(), any(), any());
        verify(activiteService).enregistrer(eq(4), eq(9), eq("MODIFICATION"), any());
        verify(reclassementRepository).enregistrer(9, 101, 2);
    }

    @Test
    void reclassifieUneCategorieMemeSiAucunHistoriqueDeCarriereExiste() {
        preparer(true, 3, 4, List.of(diplome(18, 7, 5,
                LocalDate.now().minusYears(1), 2, 3)), null);
        when(employeRepository.updateCategorie(9, 3, 2)).thenReturn(1);
        Map<String, Object> response = service.confirmer(9, 4, null);

        assertEquals("RECLASSIFIED", response.get("resultCode"));
        verify(employeRepository).updateCategorie(9, 3, 2);
        verify(situationRepository, never()).creerSituation(any(), any(), any(), any());
        verify(activiteService).enregistrer(eq(4), eq(9), eq("MODIFICATION"), any());
    }

    @Test
    void enregistreUneCategorieDansLaPeriodeCommenceeLeJourMeme() {
        LocalDate today = LocalDate.now();
        preparer(true, 3, 4, List.of(diplome(18, 7, 5,
                today, 2, 3)), situation(31, 3, today));
        when(situationRepository.modifierCategorieSituation(31, 2)).thenReturn(1);
        when(employeRepository.updateCategorie(9, 3, 2)).thenReturn(1);

        service.confirmer(9, 4, today);

        verify(situationRepository).modifierCategorieSituation(31, 2);
        verify(situationRepository, never()).fermerSituation(any(), any());
        verify(situationRepository, never()).creerSituation(any(), any(), any(), any());
    }

    @Test
    void uneNouvelleDemandeApresReclassementNeCreePasUnFauxChangement() {
        Map<String, Object> employeAvant = employe(3, 4);
        Map<String, Object> employeApres = employe(2, 3);
        Map<String, Object> situationAvant = situation(31, 3,
                LocalDate.now().minusYears(2));
        when(utilisateurRepository.estAgentSpersActif(4)).thenReturn(true);
        when(situationRepository.verrouillerEmploye(9)).thenReturn(true);
        when(employeRepository.findById(9))
                .thenReturn(Optional.of(employeAvant), Optional.of(employeApres));
        when(diplomeRepository.findDiplomesValidesPourReclassement(9))
                .thenReturn(List.of(diplome(18, 7, 5,
                        LocalDate.now().minusYears(1), 2, 3)));
        when(situationRepository.findSituationActuelle(9))
                .thenReturn(Optional.of(situationAvant), Optional.of(situationAvant),
                        Optional.of(situation(32, 2, LocalDate.now())));
        when(situationRepository.fermerSituation(
                eq(31), eq(Date.valueOf(LocalDate.now().minusDays(1)))
        )).thenReturn(1);
        when(situationRepository.creerSituation(
                eq(9), eq(2), eq(44), eq(Date.valueOf(LocalDate.now()))
        )).thenReturn(1);
        when(employeRepository.updateCategorie(9, 3, 2)).thenReturn(1);

        assertEquals("RECLASSIFIED",
                service.confirmer(9, 4, null).get("resultCode"));
        assertEquals("ALREADY_CLASSIFIED",
                service.confirmer(9, 4, null).get("resultCode"));
        verify(employeRepository, times(1)).updateCategorie(9, 3, 2);
        verify(activiteService, times(1)).enregistrer(
                eq(4), eq(9), eq("MODIFICATION"), any()
        );
    }

    @Test
    void retourne404QuandLAgentNExistePas() {
        when(utilisateurRepository.estAgentSpersActif(4)).thenReturn(true);
        when(situationRepository.verrouillerEmploye(9)).thenReturn(false);

        ReclassementException error = assertThrows(
                ReclassementException.class,
                () -> service.confirmer(9, 4, null)
        );

        assertEquals("EMPLOYE_NOT_FOUND", error.getCode());
        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }

    @Test
    void refuseLOperationSansDiplomeExploitable() {
        preparer(false, 3, 4, List.of(), null);

        ReclassementException error = assertThrows(
                ReclassementException.class,
                () -> service.proposer(9, 4)
        );

        assertEquals("NO_VALID_DIPLOMA", error.getCode());
        assertNoWrites();
    }

    @Test
    void refuseUnNiveauSansOrdreAcademique() {
        Map<String, Object> diploma = diplome(18, 7, null,
                LocalDate.now(), 2, 3);
        preparer(false, 3, 4, List.of(diploma), null);

        ReclassementException error = assertThrows(
                ReclassementException.class,
                () -> service.proposer(9, 4)
        );

        assertEquals("ACADEMIC_ORDER_MISSING", error.getCode());
    }

    @Test
    void refuseUneCategorieCibleAbsente() {
        Map<String, Object> diploma = diplome(18, 7, 5,
                LocalDate.now(), null, null);
        preparer(false, 3, 4, List.of(diploma), null);

        ReclassementException error = assertThrows(
                ReclassementException.class,
                () -> service.proposer(9, 4)
        );

        assertEquals("TARGET_CATEGORY_MISSING", error.getCode());
    }

    @Test
    void refuseUnCorpsCibleAbsent() {
        Map<String, Object> diploma = diplome(18, 7, 5,
                LocalDate.now(), 2, null);
        preparer(false, 3, 4, List.of(diploma), null);

        ReclassementException error = assertThrows(
                ReclassementException.class,
                () -> service.proposer(9, 4)
        );

        assertEquals("TARGET_CORPS_MISSING", error.getCode());
    }

    @Test
    void refuseUnAuteurQuiNestPasAutorise() {
        when(utilisateurRepository.estAgentSpersActif(4)).thenReturn(false);

        ReclassementException error = assertThrows(
                ReclassementException.class,
                () -> service.confirmer(9, 4, null)
        );

        assertEquals("FORBIDDEN", error.getCode());
        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(situationRepository, never()).verrouillerEmploye(any());
    }

    @Test
    void signaleUneIncoherenceDuCorpsDeriveSansEcritureArbitraire() {
        preparer(false, 2, 99, List.of(diplome(18, 7, 5,
                LocalDate.now(), 2, 3)), null);

        ReclassementException error = assertThrows(
                ReclassementException.class,
                () -> service.proposer(9, 4)
        );

        assertEquals("CLASSIFICATION_INCONSISTENT", error.getCode());
        assertFalse(error.getMessage().isBlank());
        verify(employeRepository, never()).updateCategorie(any(), any(), any());
    }

    @Test
    void declareLaTransactionSurLaMethodeDeConfirmation() throws Exception {
        assertTrue(ReclassementService.class
                .getMethod("confirmer", Integer.class, Integer.class, LocalDate.class)
                .isAnnotationPresent(Transactional.class));
    }

    @Test
    void uneErreurDeCreationHistoriqueInterromptLaSuiteEtLaTrace() {
        LocalDate debut = LocalDate.now().minusYears(1);
        preparer(true, 3, 4, List.of(diplome(18, 7, 5,
                LocalDate.now(), 2, 3)), situation(31, 3, debut));
        when(situationRepository.fermerSituation(
                eq(31), eq(Date.valueOf(LocalDate.now().minusDays(1)))
        )).thenReturn(1);
        when(employeRepository.updateCategorie(9, 3, 2)).thenReturn(1);
        when(situationRepository.creerSituation(
                eq(9), eq(2), eq(44), eq(Date.valueOf(LocalDate.now()))
        )).thenReturn(0);
        TrackingTransactionManager transactionManager =
                new TrackingTransactionManager();
        ProxyFactory proxyFactory = new ProxyFactory(service);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(new TransactionInterceptor(
                transactionManager,
                new AnnotationTransactionAttributeSource()
        ));
        ReclassementService transactionalService =
                (ReclassementService) proxyFactory.getProxy();

        ReclassementException error = assertThrows(
                ReclassementException.class,
                () -> transactionalService.confirmer(9, 4, null)
        );

        assertEquals("CAREER_HISTORY_UPDATE_FAILED", error.getCode());
        assertTrue(transactionManager.rolledBack);
        assertFalse(transactionManager.committed);
        verify(activiteService).enregistrer(any(), any(), any(), any());
        verify(reclassementRepository, never()).enregistrer(any(), any(), any());
    }

    @Test
    void listeLesReclassementsDepuisLeRepositoryExistant() {
        when(utilisateurRepository.estAgentSpersActif(4)).thenReturn(true);
        List<Map<String, Object>> historique = List.of(Map.of("id", 1, "categorie", "II"));
        when(reclassementRepository.historiqueGlobal()).thenReturn(historique);

        assertEquals(historique, service.obtenirHistoriqueGlobal(4));
        verify(reclassementRepository).historiqueGlobal();
    }

    private void preparer(
            boolean avecVerrou,
            Integer categorieActuelle,
            Integer corpsActuel,
            List<Map<String, Object>> diplomes,
            Map<String, Object> situation
    ) {
        when(utilisateurRepository.estAgentSpersActif(4)).thenReturn(true);
        if (avecVerrou) {
            when(situationRepository.verrouillerEmploye(9)).thenReturn(true);
        }
        when(employeRepository.findById(9))
                .thenReturn(Optional.of(employe(categorieActuelle, corpsActuel)));
        when(diplomeRepository.findDiplomesValidesPourReclassement(9))
                .thenReturn(diplomes);
        when(situationRepository.findSituationActuelle(9))
                .thenReturn(Optional.ofNullable(situation));
        when(situationRepository.findDerniereSituationOuverte(9))
                .thenReturn(Optional.empty());
    }

    private Map<String, Object> employe(Integer categorie, Integer corps) {
        Map<String, Object> employee = new HashMap<>();
        employee.put("id", 9);
        employee.put("matricule", "AG-9");
        employee.put("categorie_id", categorie);
        employee.put("categorie", categorie == null ? null
                : categorie == 3 ? "III" : "II");
        employee.put("corps_id", corps);
        employee.put("corps", corps == null ? null
                : corps == 4 ? "AGENTS_ADMINISTRATIFS" : "TECHNICIENS");
        employee.put("corps_libelle", corps == null ? null
                : corps == 4 ? "Corps administratif" : "Corps technique");
        return employee;
    }

    private Map<String, Object> diplome(
            Integer associationId,
            Integer niveauId,
            Integer ordreAcademique,
            LocalDate dateObtention,
            Integer categorieId,
            Integer corpsId
    ) {
        Map<String, Object> diploma = new HashMap<>();
        diploma.put("employe_diplome_id", associationId);
        diploma.put("intitule", "Niveau " + niveauId);
        diploma.put("niveau_id", niveauId);
        diploma.put("niveau", "Niveau " + ordreAcademique);
        diploma.put("ordre_academique", ordreAcademique);
        diploma.put("date_obtention", dateObtention);
        diploma.put("categorie_id", categorieId);
        diploma.put("categorie_code", categorieId == null ? null
                : categorieId == 2 ? "II" : "VI");
        diploma.put("corps_id", corpsId);
        diploma.put("corps_code", corpsId == null ? null
                : corpsId == 3 ? "TECHNICIENS" : "CADRES_SUPERIEURS");
        diploma.put("corps_libelle", corpsId == null ? null : "Corps cible");
        return diploma;
    }

    private Map<String, Object> situation(
            Integer historyId,
            Integer categorieId,
            LocalDate debut
    ) {
        Map<String, Object> career = new HashMap<>();
        career.put("historique_id", historyId);
        career.put("categorie_id", categorieId);
        career.put("echelon_id", 44);
        career.put("date_debut", debut);
        return career;
    }

    private void assertNoWrites() {
        verify(employeRepository, never()).updateCategorie(any(), any(), any());
        verify(activiteService, never()).enregistrer(any(), any(), any(), any());
    }

    private static final class TrackingTransactionManager
            implements PlatformTransactionManager {

        private boolean committed;
        private boolean rolledBack;

        @Override
        public TransactionStatus getTransaction(
                TransactionDefinition definition
        ) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            committed = true;
        }

        @Override
        public void rollback(TransactionStatus status) {
            rolledBack = true;
        }
    }
}

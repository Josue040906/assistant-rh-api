package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.AffectationRepository;
import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import com.assistantrh.assistant_rh_api.repository.NotificationRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentCycleServiceTests {
    private final DocumentRhRepository documents = mock(DocumentRhRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);
    private final ActiviteService activites = mock(ActiviteService.class);
    private final SituationCarriereRepository situations = mock(SituationCarriereRepository.class);
    private final AffectationRepository affectations = mock(AffectationRepository.class);
    private final AvancementService avancement = mock(AvancementService.class);
    private final AffectationService affectation = mock(AffectationService.class);
    private final RetraiteService retraite = mock(RetraiteService.class);
    private final CongeService conge = mock(CongeService.class);
    private final TitularisationService titularisation = mock(TitularisationService.class);
    private final DocumentCycleService service = new DocumentCycleService(documents, notifications,
            utilisateurs, activites, situations, affectations, avancement, affectation, retraite, conge,
            titularisation);

    private final LocalDate hier = LocalDate.now().minusDays(1);

    private static final String REFERENCE = "Arrêté n° 12/2026 — demande DOC-2026-00009";

    private Map<String, Object> demande(String type, Integer auteurId, LocalDate dateEffet, Map<String, Object> donnees) {
        return demande(type, "ACCEPTE", auteurId, dateEffet, donnees);
    }

    private Map<String, Object> demande(String type, String statut, Integer auteurId, LocalDate dateEffet,
                                        Map<String, Object> donnees) {
        Map<String, Object> ligne = new HashMap<>(Map.of(
                "id", 9, "statut", statut, "employe_id", 42,
                "reference_document", "DOC-2026-00009", "type_code", type, "donnees", donnees));
        ligne.put("auteur_id", auteurId);
        ligne.put("date_effet", dateEffet);
        ligne.put("decision_reference", "ACCEPTE".equals(statut) ? "Arrêté n° 12/2026" : null);
        when(documents.trouverPourCycle(9L)).thenReturn(Optional.of(ligne));
        when(documents.changerStatut(9L, "APPLIQUE", List.of("ACCEPTE"))).thenReturn(true);
        when(utilisateurs.estAgentSpersActif(5)).thenReturn(true);
        return ligne;
    }

    @Test
    void uneDemandeAccepteeSAppliqueSonEffetEstProduitEtLeChefNotifie() {
        demande("AVANCEMENT", 5, hier, Map.of("situationActuelle", Map.of("echelonId", 4)));
        when(situations.findSituationActuelle(42)).thenReturn(Optional.of(Map.of("echelon_id", 4)));

        service.appliquerDocument(9L, 5);

        verify(avancement).confirmer(42, 5, hier, REFERENCE);
        verify(notifications).notifierDocumentApplique(9L, 5);
        verify(activites).enregistrer(5, 42, "APPLICATION_DOCUMENT", "Application de la demande DOC-2026-00009");
    }

    @Test
    void uneDemandeNEstPasApplicableAvantSaDateDEffet() {
        LocalDate demain = LocalDate.now().plusDays(1);
        demande("AVANCEMENT", 5, demain, Map.of());

        IllegalArgumentException refus = assertThrows(IllegalArgumentException.class,
                () -> service.appliquerDocument(9L, 5));

        assertEquals("Cette demande sera applicable à partir du "
                + demain.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".", refus.getMessage());
        verify(documents, never()).changerStatut(anyLong(), any(), any());
        verifyNoInteractions(avancement);
    }

    @Test
    void unAgentDuPersonnelQuiNEstPasLAuteurAppliqueLaDemandeAcceptee() {
        demande("RETRAITE", 7, hier, Map.of());

        service.appliquerDocument(9L, 5);

        verify(retraite).prendreRetraite(42, 5, hier, REFERENCE);
    }

    @Test
    void horsDuPersonnelPersonneNApplique() {
        demande("AVANCEMENT", 5, hier, Map.of());

        assertEquals("Seul un agent actif du Service du Personnel peut le faire.",
                assertThrows(IllegalArgumentException.class, () -> service.appliquerDocument(9L, 8)).getMessage());
        verify(notifications, never()).notifierDocumentApplique(anyLong(), anyInt());
    }

    @Test
    void uneDemandeSansReponseNeSAppliquePas() {
        demande("AVANCEMENT", "CREE", 5, hier, Map.of());

        assertEquals("Seule une demande acceptée peut être appliquée.",
                assertThrows(IllegalArgumentException.class, () -> service.appliquerDocument(9L, 5)).getMessage());
        verifyNoInteractions(avancement);
    }

    @Test
    void laReponseAccepteeSEnregistreAvecSonSignataireEtLaDateAccordee() {
        demande("AVANCEMENT", "CREE", 5, LocalDate.now().plusDays(10), Map.of());
        when(documents.enregistrerDecision(9L, "ACCEPTE", "Le Directeur des RH", "Arrêté n° 12/2026",
                hier, null, hier, 5)).thenReturn(true);

        service.deciderDocument(9L, 5, true, " Le Directeur des RH ", "Arrêté n° 12/2026", hier, null, hier);

        verify(notifications).notifierDecision(9L, 5, true);
        verify(activites).enregistrer(eq(5), eq(42), eq("ACCEPTATION_DOCUMENT"), any());
    }

    @Test
    void uneAcceptationExigeLaReferenceDeLActeEtUnRefusSonSignataire() {
        demande("AVANCEMENT", "CREE", 5, hier, Map.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.deciderDocument(9L, 5, true, "Le Directeur", " ", hier, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.deciderDocument(9L, 5, false, null, null, hier, "Budget", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.deciderDocument(9L, 5, false, "Le Directeur", null, LocalDate.now().plusDays(1), null, null));
        verify(documents, never()).enregistrerDecision(anyLong(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void uneDemandeDejaRepondueNeChangePasDeReponse() {
        demande("AVANCEMENT", "ACCEPTE", 5, hier, Map.of());

        assertEquals("La réponse d'une demande ne se saisit qu'une fois, tant qu'elle est créée.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.deciderDocument(9L, 5, false, "Le Directeur", null, hier, null, null)).getMessage());
    }

    @Test
    void unAvancementDontLaSituationAChangeNEstPasApplique() {
        demande("AVANCEMENT", 5, hier, Map.of("situationActuelle", Map.of("echelonId", 4)));
        when(situations.findSituationActuelle(42)).thenReturn(Optional.of(Map.of("echelon_id", 5)));

        assertThrows(IllegalArgumentException.class, () -> service.appliquerDocument(9L, 5));
        verifyNoInteractions(avancement);
    }

    @Test
    void unChangementDeLieuSeulGardeLePosteEtLeServiceActuels() {
        Map<String, Object> cible = new HashMap<>();
        cible.put("posteId", null);
        cible.put("serviceId", null);
        cible.put("lieuTravail", "Toamasina");
        demande("MUTATION", 5, hier, Map.of("situationDemandee", cible));
        when(affectations.findActiveByEmployeId(42)).thenReturn(Map.of("poste_id", 3, "service_id", 2));

        service.appliquerDocument(9L, 5);

        verify(affectation).modifierAffectation(42, 5, 3, 2, "Toamasina", hier,
                REFERENCE, REFERENCE);
    }

    @Test
    void unNouveauServiceSansPosteNePeutPasEtreApplique() {
        Map<String, Object> cible = new HashMap<>();
        cible.put("posteId", null);
        cible.put("serviceId", 8);
        demande("MUTATION", 5, hier, Map.of("situationDemandee", cible));
        when(affectations.findActiveByEmployeId(42)).thenReturn(Map.of("poste_id", 3, "service_id", 2));

        assertEquals("Précisez le poste dans le nouveau service : la demande ne l'indique pas.",
                assertThrows(IllegalArgumentException.class, () -> service.appliquerDocument(9L, 5)).getMessage());
        verifyNoInteractions(affectation);
    }

    @Test
    void uneRetraiteAppliqueeFaitPartirLAgentParLaMemeActionQueLeBouton() {
        demande("RETRAITE", 5, hier, Map.of());

        service.appliquerDocument(9L, 5);

        verify(retraite).prendreRetraite(42, 5, hier, REFERENCE);
    }

    @Test
    void uneTitularisationAppliqueeTitulariseLAgentParLaMemeActionQueLeBouton() {
        demande("TITULARISATION", 5, hier, Map.of(
                "situationActuelle", Map.of("echelonId", 24), "situationDemandee", Map.of("echelonId", 5)));
        when(situations.findSituationActuelle(42)).thenReturn(Optional.of(Map.of("echelon_id", 24)));

        service.appliquerDocument(9L, 5);

        verify(titularisation).titulariser(42, 5, hier, REFERENCE);
    }

    @Test
    void unCongeAppliqueEnregistreLAbsence() {
        demande("CONGE", 5, hier, Map.of("dateDebut", hier.toString(), "dateFin", hier.plusDays(2).toString()));

        service.appliquerDocument(9L, 5);

        verify(conge).prendreConge(42, 5, hier, hier.plusDays(2), 9L, REFERENCE);
    }

    @Test
    void unChefPeutAnnulerLaDemandeDUnAutre() {
        demande("CONGE", "CREE", 5, hier, Map.of());
        when(utilisateurs.estChefActif(7)).thenReturn(true);
        when(documents.changerStatut(9L, "ANNULE", List.of("CREE"))).thenReturn(true);

        service.annulerDocument(9L, 7);

        verify(activites).enregistrer(7, 42, "ANNULATION_DOCUMENT", "Annulation du document DOC-2026-00009");
    }

    @Test
    void lArchivageNAccepteQuUneDemandeAppliqueeRefuseeOuAnnulee() {
        demande("CONGE", 5, hier, Map.of());
        when(documents.changerStatut(9L, "ARCHIVE", List.of("APPLIQUE", "REFUSE", "ANNULE"))).thenReturn(false);

        assertEquals("Seule une demande appliquée, refusée ou annulée peut être archivée.",
                assertThrows(IllegalArgumentException.class, () -> service.archiverDocument(9L, 5)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.archiverDocument(0L, 5));
        verify(documents, never()).changerStatut(eq(0L), any(), any());
    }
}

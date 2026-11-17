package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import com.assistantrh.assistant_rh_api.repository.NotificationRepository;
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
import static org.mockito.ArgumentMatchers.anyMap;
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
    private final DemandeApplicabiliteService applicabilite = mock(DemandeApplicabiliteService.class);
    private final AvancementService avancement = mock(AvancementService.class);
    private final AffectationService affectation = mock(AffectationService.class);
    private final RetraiteService retraite = mock(RetraiteService.class);
    private final CongeService conge = mock(CongeService.class);
    private final TitularisationService titularisation = mock(TitularisationService.class);
    private final DocumentCycleService service = new DocumentCycleService(documents, notifications,
            utilisateurs, activites, applicabilite, avancement, affectation, retraite, conge,
            titularisation);

    private final LocalDate hier = LocalDate.now().minusDays(1);

    private static final String REFERENCE = "Décision signée par Le Directeur des RH — demande DOC-2026-00009";

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
        ligne.put("decision_signataire", "ACCEPTE".equals(statut) ? "Le Directeur des RH" : null);
        when(documents.trouverPourCycle(9L)).thenReturn(Optional.of(ligne));
        when(documents.changerStatut(9L, "APPLIQUE", List.of("ACCEPTE"))).thenReturn(true);
        when(utilisateurs.estAgentSpersActif(5)).thenReturn(true);
        return ligne;
    }

    @Test
    void uneDemandeAccepteeSAppliqueSonEffetEstProduitEtLeChefNotifie() {
        demande("AVANCEMENT", 5, hier, Map.of("situationActuelle", Map.of("echelonId", 4)));

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
    void uneDemandeDevenueImpossibleNEstPasAccepteeEtLaRaisonEstDonnee() {
        LocalDate debut = LocalDate.now().plusDays(20);
        demande("CONGE", "CREE", 5, debut, Map.of("dateDebut", debut.toString(), "dateFin", debut.plusDays(4).toString()));
        when(applicabilite.empechement(anyMap()))
                .thenReturn(Optional.of("L'agent a déjà un congé du 01/12/2026 au 05/12/2026 sur ces dates."));

        assertEquals("Cette demande ne peut pas être acceptée : L'agent a déjà un congé du 01/12/2026 au 05/12/2026"
                        + " sur ces dates. Saisissez un refus ou annulez-la.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.deciderDocument(9L, 5, true, "Le Directeur", hier, null, null)).getMessage());
        verify(documents, never()).enregistrerDecision(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void lAcceptationVerifieLaDemandeALaDateAccordee() {
        demande("MUTATION", "CREE", 5, LocalDate.now().plusDays(10), Map.of());
        when(documents.enregistrerDecision(9L, "ACCEPTE", "Le Directeur", hier, null, hier, 5)).thenReturn(true);

        service.deciderDocument(9L, 5, true, "Le Directeur", hier, null, hier);

        verify(applicabilite).empechement(org.mockito.ArgumentMatchers.<Map<String, Object>>argThat(
                document -> hier.equals(document.get("date_effet"))));
    }

    @Test
    void unCongeQuiChevaucheResteRefusable() {
        LocalDate debut = LocalDate.now().plusDays(20);
        demande("CONGE", "CREE", 5, debut, Map.of("dateDebut", debut.toString(), "dateFin", debut.plusDays(4).toString()));
        when(documents.enregistrerDecision(9L, "REFUSE", "Le Directeur", hier, null, null, 5)).thenReturn(true);

        service.deciderDocument(9L, 5, false, "Le Directeur", hier, null, null);

        verify(applicabilite, never()).empechement(anyMap());
        verify(notifications).notifierDecision(9L, 5, false);
    }

    @Test
    void laReponseAccepteeSEnregistreAvecSonSignataireEtLaDateAccordee() {
        demande("AVANCEMENT", "CREE", 5, LocalDate.now().plusDays(10), Map.of());
        when(documents.enregistrerDecision(9L, "ACCEPTE", "Le Directeur des RH",
                hier, null, hier, 5)).thenReturn(true);

        service.deciderDocument(9L, 5, true, " Le Directeur des RH ", hier, null, hier);

        verify(notifications).notifierDecision(9L, 5, true);
        verify(activites).enregistrer(eq(5), eq(42), eq("ACCEPTATION_DOCUMENT"), any());
    }

    @Test
    void uneReponseExigeSonSignataireEtUneDatePassee() {
        demande("AVANCEMENT", "CREE", 5, hier, Map.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.deciderDocument(9L, 5, true, " ", hier, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.deciderDocument(9L, 5, false, null, hier, "Budget", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.deciderDocument(9L, 5, false, "Le Directeur", LocalDate.now().plusDays(1), null, null));
        verify(documents, never()).enregistrerDecision(anyLong(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void laReponseNePeutPasPrecederLaDemande() {
        demande("AVANCEMENT", "CREE", 5, hier, Map.of()).put("date_document", LocalDate.now());

        assertEquals("La réponse ne peut pas précéder la demande (du "
                        + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ").",
                assertThrows(IllegalArgumentException.class,
                        () -> service.deciderDocument(9L, 5, true, "Le Directeur", hier, null, null)).getMessage());
    }

    @Test
    void uneDemandeDejaRepondueNeChangePasDeReponse() {
        demande("AVANCEMENT", "ACCEPTE", 5, hier, Map.of());

        assertEquals("La réponse d'une demande ne se saisit qu'une fois, tant qu'elle est créée.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.deciderDocument(9L, 5, false, "Le Directeur", hier, null, null)).getMessage());
    }

    @Test
    void uneDemandeDevenueImpossibleNEstPasAppliqueeEtInviteAAnnuler() {
        demande("AVANCEMENT", 5, hier, Map.of("situationActuelle", Map.of("echelonId", 4)));
        when(applicabilite.empechement(anyMap())).thenReturn(Optional.of(
                "La situation de carrière de l'agent a changé depuis la demande : elle ne peut plus être appliquée."));

        assertEquals("La situation de carrière de l'agent a changé depuis la demande : elle ne peut plus être appliquée."
                        + " Annulez cette demande : elle ne pourra pas être appliquée.",
                assertThrows(IllegalArgumentException.class, () -> service.appliquerDocument(9L, 5)).getMessage());
        verify(documents, never()).changerStatut(anyLong(), any(), any());
        verifyNoInteractions(avancement);
    }

    @Test
    void uneMutationAppliqueeVaVersLaCibleDeLaDemande() {
        Map<String, Object> donnees = Map.of("situationDemandee", Map.of("lieuTravail", "Toamasina"));
        demande("MUTATION", 5, hier, donnees);
        when(applicabilite.cibleMutation(42, donnees))
                .thenReturn(new DemandeApplicabiliteService.CibleMutation(3, 2, "Toamasina"));

        service.appliquerDocument(9L, 5);

        verify(affectation).modifierAffectation(42, 5, 3, 2, "Toamasina", hier,
                REFERENCE, REFERENCE);
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
        when(documents.annuler(9L, null, 7, List.of("CREE", "ACCEPTE"))).thenReturn(true);

        service.annulerDocument(9L, 7, null);

        verify(activites).enregistrer(7, 42, "ANNULATION_DOCUMENT", "Annulation de la demande DOC-2026-00009");
    }

    @Test
    void uneDemandeAccepteeSAnnuleAvecSonMotifGardeDansLeJournal() {
        demande("AVANCEMENT", "ACCEPTE", 5, hier, Map.of());
        when(documents.annuler(9L, "Reclassement intervenu", 5, List.of("CREE", "ACCEPTE"))).thenReturn(true);

        service.annulerDocument(9L, 5, " Reclassement intervenu ");

        verify(activites).enregistrer(5, 42, "ANNULATION_DOCUMENT",
                "Annulation de la demande DOC-2026-00009 : Reclassement intervenu");
    }

    @Test
    void annulerUneDemandeAccepteeExigeUnMotif() {
        demande("AVANCEMENT", "ACCEPTE", 5, hier, Map.of());

        assertEquals("Indiquez pourquoi vous annulez cette demande : elle a déjà été acceptée.",
                assertThrows(IllegalArgumentException.class, () -> service.annulerDocument(9L, 5, " ")).getMessage());
        verify(documents, never()).annuler(anyLong(), any(), any(), any());
    }

    @Test
    void uneDemandeAppliqueeNeSAnnulePlus() {
        demande("AVANCEMENT", "APPLIQUE", 5, hier, Map.of());

        assertEquals("Seule une demande créée ou acceptée peut être annulée.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.annulerDocument(9L, 5, "Erreur")).getMessage());
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

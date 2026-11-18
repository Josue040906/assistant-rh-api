package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.MessageAgentRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageAgentServiceTests {
    private final MessageAgentRepository repository = mock(MessageAgentRepository.class);
    private final EmployeRepository employes = mock(EmployeRepository.class);
    private final DocumentRhRepository documents = mock(DocumentRhRepository.class);
    private final UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);
    private final EnvoiEmail envoi = mock(EnvoiEmail.class);
    private final MessageAgentService service =
            new MessageAgentService(repository, employes, documents, utilisateurs, envoi);

    private final LocalDate effet = LocalDate.of(2026, 11, 20);

    @BeforeEach
    void agent() {
        Map<String, Object> hery = new HashMap<>(Map.of("prenom", "Hery", "nom", "RANDRIAKOTOMANGA", "sexe", "M",
                "poste", "Juriste", "service", "Service Juridique", "lieu_travail", "Siège administratif",
                "grade_libelle", "1re classe, 2e échelon", "grade_code", "1C2E", "indice", 720));
        when(employes.findById(1)).thenReturn(Optional.of(hery));
        when(utilisateurs.estAgentSpersActif(5)).thenReturn(true);
    }

    @Test
    void uneMutationPrepareUnBrouillonRedigeSansRienEnvoyer() {
        service.mutationEnregistree(1, effet);

        ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
        verify(repository).proposer(eq(1), eq("MUTATION"), eq("Votre nouvelle affectation"), corps.capture(),
                eq("mutation:1:2026-11-20"));
        assertTrue(corps.getValue().startsWith("Bonjour Monsieur Hery RANDRIAKOTOMANGA,"));
        assertTrue(corps.getValue().contains("à compter du 20/11/2026 : vous occuperez le poste de Juriste au Service Juridique, à Siège administratif."));
        verify(envoi, never()).envoyer(any(), any(), any(), any());
    }

    @Test
    void uneDemandeRefuseeDonneSonMotif() {
        service.decisionSurDemande(39L, 1, "Demande de mutation", "DOC-2026-00008", false, null, "Poste complet");

        ArgumentCaptor<String> corps = ArgumentCaptor.forClass(String.class);
        verify(repository).proposer(eq(1), eq("DEMANDE_REFUSEE"),
                eq("Votre demande n'a pas été retenue : demande de mutation"), corps.capture(), eq("demande-refusee:39"));
        assertTrue(corps.getValue().contains("Votre demande de mutation (DOC-2026-00008) n'a pas été retenue : Poste complet."));
    }

    @Test
    void lEnvoiPartAvecLeTexteRelu() {
        when(repository.trouver(7L)).thenReturn(Optional.of(Map.of("statut", "PROPOSE", "email_agent", "hery@mef.gov.mg",
                "prenom", "Hery", "nom", "RANDRIAKOTOMANGA", "sujet", "Brouillon", "corps", "Texte du brouillon")));
        when(envoi.envoyer("hery@mef.gov.mg", "Hery RANDRIAKOTOMANGA", "Sujet relu", "Texte relu")).thenReturn("JOURNAL");

        service.envoyer(7L, 5, " Sujet relu ", "Texte relu");

        verify(repository).enregistrerEnvoi(7L, "ENVOYE", "hery@mef.gov.mg", "Sujet relu", "Texte relu", 5, "JOURNAL", null);
    }

    @Test
    void unAgentSansEmailNeRecoitRienEtOnLeDit() {
        when(repository.trouver(7L)).thenReturn(Optional.of(Map.of("statut", "PROPOSE", "sujet", "S", "corps", "C")));

        assertEquals("Cet agent n'a pas d'adresse e-mail : ajoutez-la sur sa fiche, puis envoyez.",
                assertThrows(IllegalArgumentException.class, () -> service.envoyer(7L, 5, null, null)).getMessage());
        verify(envoi, never()).envoyer(any(), any(), any(), any());
    }

    @Test
    void unRefusDuFournisseurEstGardeEtPeutEtreRetente() {
        when(repository.trouver(7L)).thenReturn(Optional.of(Map.of("statut", "PROPOSE", "email_agent", "hery@mef.gov.mg",
                "prenom", "Hery", "nom", "R", "sujet", "S", "corps", "C")));
        when(envoi.envoyer(any(), any(), any(), any())).thenThrow(new IllegalStateException("Brevo a refusé l'envoi (401)"));

        assertThrows(IllegalStateException.class, () -> service.envoyer(7L, 5, null, null));
        verify(repository).enregistrerEnvoi(7L, "ECHEC", "hery@mef.gov.mg", "S", "C", 5, null, "Brevo a refusé l'envoi (401)");
    }

    @Test
    void horsDuPersonnelOnNeGerePasLesEmails() {
        assertThrows(IllegalArgumentException.class, () -> service.envoyer(7L, 8, null, null));
        assertThrows(IllegalArgumentException.class, () -> service.ecarter(7L, 8));
        verify(repository, never()).ecarter(anyLong(), any());
    }

    @Test
    void leModeJournalNEnvoieRienEtLeDit() {
        EnvoiEmail journal = new EnvoiEmail("", "", "SYGPERS – Service du Personnel", null);

        assertEquals("JOURNAL", journal.envoyer("hery@mef.gov.mg", "Hery", "S", "C"));
        assertEquals(false, journal.reel());
    }
}

package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import com.assistantrh.assistant_rh_api.repository.NotificationRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;

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
import static org.mockito.Mockito.when;

class DocumentCycleServiceTests {
    private final DocumentRhRepository documents = mock(DocumentRhRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);
    private final ActiviteService activites = mock(ActiviteService.class);
    private final DocumentCycleService service =
            new DocumentCycleService(documents, notifications, utilisateurs, activites);

    private void document(Integer auteurId) {
        Map<String, Object> ligne = new HashMap<>(Map.of(
                "id", 9, "statut", "BROUILLON", "employe_id", 42, "reference_document", "DOC-2026-00009"));
        ligne.put("auteur_id", auteurId);
        when(documents.trouverPourCycle(9L)).thenReturn(Optional.of(ligne));
    }

    @Test
    void lAuteurValideSaDemandeLeChefEstNotifieEtLActiviteJournalisee() {
        document(5);
        when(documents.changerStatut(9L, "VALIDE", List.of("BROUILLON"))).thenReturn(true);

        service.validerDocument(9L, 5);

        verify(notifications).notifierDocumentValide(9L, 5);
        verify(activites).enregistrer(5, 42, "VALIDATION", "Validation du document DOC-2026-00009");
    }

    @Test
    void personneDAutreQueLAuteurNeValide() {
        document(5);
        when(utilisateurs.estChefActif(7)).thenReturn(true);

        IllegalArgumentException refus = assertThrows(IllegalArgumentException.class,
                () -> service.validerDocument(9L, 7));

        assertEquals("Seul l'auteur de la demande peut la valider.", refus.getMessage());
        verify(documents, never()).changerStatut(anyLong(), any(), any());
        verify(notifications, never()).notifierDocumentValide(anyLong(), anyInt());
    }

    @Test
    void sansAuteurConnuUnChefValideMaisPasUnAgent() {
        document(null);
        when(utilisateurs.estChefActif(7)).thenReturn(true);
        when(documents.changerStatut(9L, "VALIDE", List.of("BROUILLON"))).thenReturn(true);

        service.validerDocument(9L, 7);
        assertThrows(IllegalArgumentException.class, () -> service.validerDocument(9L, 8));
        verify(notifications).notifierDocumentValide(9L, 7);
    }

    @Test
    void onNeValideNiNAnnuleCeQuiNEstPlusUnBrouillon() {
        document(5);
        when(documents.changerStatut(eq(9L), any(), eq(List.of("BROUILLON")))).thenReturn(false);

        assertEquals("Seul un brouillon peut être validé.",
                assertThrows(IllegalArgumentException.class, () -> service.validerDocument(9L, 5)).getMessage());
        assertEquals("Seul un brouillon peut être annulé.",
                assertThrows(IllegalArgumentException.class, () -> service.annulerDocument(9L, 5)).getMessage());
        verify(notifications, never()).notifierDocumentValide(anyLong(), anyInt());
    }

    @Test
    void unChefPeutAnnulerLeBrouillonDUnAutre() {
        document(5);
        when(utilisateurs.estChefActif(7)).thenReturn(true);
        when(documents.changerStatut(9L, "ANNULE", List.of("BROUILLON"))).thenReturn(true);

        service.annulerDocument(9L, 7);

        verify(activites).enregistrer(7, 42, "ANNULATION_DOCUMENT", "Annulation du document DOC-2026-00009");
    }

    @Test
    void lArchivageNAccepteQuUnDocumentValideOuAnnule() {
        document(5);
        when(documents.changerStatut(9L, "ARCHIVE", List.of("VALIDE", "ANNULE"))).thenReturn(false);

        assertEquals("Seul un document validé ou annulé peut être archivé.",
                assertThrows(IllegalArgumentException.class, () -> service.archiverDocument(9L, 5)).getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.archiverDocument(0L, 5));
    }
}

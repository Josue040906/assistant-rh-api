package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.AFaireRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AFaireServiceTests {
    private final AFaireRepository aFaire = mock(AFaireRepository.class);
    private final UtilisateurRepository utilisateurs = mock(UtilisateurRepository.class);
    private final AFaireService service = new AFaireService(aFaire, utilisateurs);

    @Test
    void listeCeQuiAttendUneActionDuPlusUrgentAuMoinsUrgent() {
        when(utilisateurs.estChefActif(7)).thenReturn(true);
        when(aFaire.comptesEnAttente()).thenReturn(2);
        when(aFaire.documentsAuStatut("BROUILLON")).thenReturn(6);
        when(aFaire.dossiersIncomplets()).thenReturn(24);

        assertEquals(List.of(
                new AFaireService.Element("comptes_en_attente",
                        "Demandes d'inscription en attente", 2, "/administration/demandes-inscription"),
                new AFaireService.Element("documents_brouillon", "Documents en brouillon", 6, "/documents?statut=BROUILLON"),
                new AFaireService.Element("dossiers_incomplets",
                        "Dossiers d'agents incomplets", 24, "/agents?dossier=incomplet")
        ), service.pour(7).elements());
    }

    @Test
    void lesComptesEnAttenteNeSontVusQueParUnChefEtLesZerosSontOmis() {
        when(utilisateurs.estChefActif(8)).thenReturn(false);
        when(aFaire.documentsAuStatut("BROUILLON")).thenReturn(1);

        assertEquals(
                List.of(new AFaireService.Element("documents_brouillon",
                        "Documents en brouillon", 1, "/documents?statut=BROUILLON")),
                service.pour(8).elements()
        );
        verify(aFaire, never()).comptesEnAttente();
    }
}

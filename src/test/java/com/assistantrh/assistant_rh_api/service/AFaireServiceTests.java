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
    private final com.assistantrh.assistant_rh_api.repository.DocumentRhRepository documents =
            mock(com.assistantrh.assistant_rh_api.repository.DocumentRhRepository.class);
    private final PosteService postes = mock(PosteService.class);
    private final AFaireService service = new AFaireService(aFaire, utilisateurs, documents, postes);

    @Test
    void listeCeQuiAttendUneActionDuPlusUrgentAuMoinsUrgent() {
        when(utilisateurs.estChefActif(7)).thenReturn(true);
        when(aFaire.comptesEnAttente()).thenReturn(2);
        when(aFaire.demandesApplicables()).thenReturn(6);
        when(aFaire.dossiersIncomplets()).thenReturn(24);

        assertEquals(List.of(
                new AFaireService.Element("comptes_en_attente",
                        "Demandes d'inscription en attente", 2, "/administration/demandes-inscription"),
                new AFaireService.Element("demandes_applicables", "Demandes à appliquer", 6, "/documents?statut=APPLICABLE"),
                new AFaireService.Element("dossiers_incomplets",
                        "Dossiers d'agents incomplets", 24, "/agents?dossier=incomplet")
        ), service.pour(7).elements());
    }

    @Test
    void lesComptesEnAttenteNeSontVusQueParUnChefEtLesZerosSontOmis() {
        when(utilisateurs.estChefActif(8)).thenReturn(false);
        when(aFaire.demandesApplicables()).thenReturn(1);

        assertEquals(
                List.of(new AFaireService.Element("demandes_applicables",
                        "Demandes à appliquer", 1, "/documents?statut=APPLICABLE")),
                service.pour(8).elements()
        );
        verify(aFaire, never()).comptesEnAttente();
    }

    @Test
    void lePointDuJourAnnonceCeQuiArriveAvecLaListeDesAgentsConcernes() {
        when(aFaire.ancienneteAtteinteEntre(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(3);
        when(documents.ancienneteRetraiteAnnees()).thenReturn(java.util.Optional.of(25));
        when(aFaire.embauchesEntre(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(2);
        when(postes.etatDesPostes(null, null)).thenReturn(List.of(
                java.util.Map.of("statut", "SE_LIBERE"), java.util.Map.of("statut", "LIBRE")));

        List<AFaireService.Element> elements = service.pour(7).elements();

        AFaireService.Element anciennete = elements.stream()
                .filter(e -> e.code().equals("anciennetes_du_mois")).findFirst().orElseThrow();
        assertEquals(3, anciennete.nombre());
        String critere = java.net.URLDecoder.decode(anciennete.route().substring("/agents?recherche=".length()),
                java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(true, critere.contains("\"field\":\"date_anciennete_echelon\",\"operator\":\"BETWEEN\""));
        assertEquals(2, elements.stream().filter(e -> e.code().equals("retraites_de_l_annee")).findFirst().orElseThrow().nombre());
        assertEquals(1, elements.stream().filter(e -> e.code().equals("postes_se_liberent")).findFirst().orElseThrow().nombre());
    }
}

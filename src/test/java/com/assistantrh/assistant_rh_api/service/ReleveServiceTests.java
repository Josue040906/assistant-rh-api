package com.assistantrh.assistant_rh_api.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReleveServiceTests {
    private final PosteService postes = mock(PosteService.class);
    private final com.assistantrh.assistant_rh_api.repository.PlanReleveRepository plans =
            mock(com.assistantrh.assistant_rh_api.repository.PlanReleveRepository.class);
    private final ReleveService service = new ReleveService(postes, plans);
    private final LocalDate aujourdhui = LocalDate.now();

    private static Map<String, Object> occupant(int id, String prenom, LocalDate depart, String motif, LocalDate droit) {
        Map<String, Object> occupant = new HashMap<>(Map.of("id", id, "prenom", prenom, "nom", "TEST"));
        occupant.put("depart_prevu", depart);
        occupant.put("motif_depart", motif);
        occupant.put("retraite_possible_le", droit);
        return occupant;
    }

    private static Map<String, Object> poste(int id, String intitule, int places, String service,
                                             List<Map<String, Object>> occupants) {
        return new HashMap<>(Map.of("id", id, "intitule", intitule, "nombre_places", places, "service_id", service.hashCode(),
                "service", service, "direction", "DGA", "occupants", occupants));
    }

    @Test
    void lesDepartsLesPostesARisqueEtLesServicesTouches() {
        LocalDate dansTroisAns = aujourdhui.plusYears(3);
        LocalDate dansDixAns = aujourdhui.plusYears(10);
        when(postes.etatDesPostes(isNull(), isNull(), any())).thenReturn(List.of(
                // Un chef dont le droit au départ est dépassé : élevé, et à régulariser.
                poste(5, "Chef de service", 1, "Service du Personnel",
                        List.of(occupant(1, "Lova", aujourdhui, "DROIT_RETRAITE", aujourdhui.minusYears(1)))),
                // Un poste à deux places dont un seul titulaire part : à surveiller.
                poste(8, "Gestionnaire de dossiers", 3, "Service du Personnel",
                        List.of(occupant(2, "Hery", dansTroisAns, "DROIT_RETRAITE", dansTroisAns),
                                occupant(3, "Faly", null, null, null))),
                // Une demande de retraite au-delà de l'horizon n'y entre pas.
                poste(9, "Juriste", 1, "Service Juridique",
                        List.of(occupant(4, "Aina", dansDixAns, "DEMANDE_RETRAITE", null)))));

        Map<String, Object> tableau = service.tableau(5);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> departs = (List<Map<String, Object>>) tableau.get("departs");
        assertEquals(List.of(1, 2), departs.stream().map(depart -> depart.get("id")).toList());
        assertEquals(true, departs.get(0).get("en_retard"));
        assertEquals(false, departs.get(1).get("en_retard"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> risques = (List<Map<String, Object>>) tableau.get("postes");
        assertEquals(List.of("ELEVE", "A_SURVEILLER"), risques.stream().map(p -> p.get("niveau")).toList());
        assertEquals(true, risques.get(0).get("se_vide"));

        @SuppressWarnings("unchecked")
        Map<String, Object> chiffres = (Map<String, Object>) tableau.get("chiffres");
        assertEquals(2, chiffres.get("departs"));
        assertEquals(1L, chiffres.get("postes_risque_eleve"));
        assertEquals(1, chiffres.get("services_touches"));
    }

    @Test
    void unSuccesseurDesigneEstRattacheAuPosteEtNeCompteplusCommeSansReleve() {
        when(postes.etatDesPostes(isNull(), isNull(), any())).thenReturn(List.of(
                poste(5, "Chef de service", 1, "Service du Personnel",
                        List.of(occupant(1, "Lova", aujourdhui.plusYears(1), "DROIT_RETRAITE", aujourdhui.plusYears(1))))));
        when(plans.plans()).thenReturn(List.of(Map.of("poste_id", 5, "successeur_id", 7, "statut", "EN_PREPARATION")));

        Map<String, Object> tableau = service.tableau(2);

        @SuppressWarnings("unchecked")
        Map<String, Object> releve = (Map<String, Object>) ((List<Map<String, Object>>) tableau.get("postes")).get(0).get("releve");
        assertEquals(7, releve.get("successeur_id"));
        assertEquals(0L, ((Map<?, ?>) tableau.get("chiffres")).get("risque_eleve_sans_releve"));
    }

    @Test
    void onNeDesignePasUnTitulaireNiUnePreparationInconnue() {
        when(plans.situationAgent(1, 5)).thenReturn(Map.of("poste_existe", true, "actif", true, "titulaire", true));
        assertEquals("Cet agent occupe déjà ce poste : choisissez un autre successeur.",
                assertThrows(IllegalArgumentException.class, () -> service.designer(5, 1, null, null, 9)).getMessage());
        assertEquals("La préparation est A_PREPARER, EN_PREPARATION ou PRET.",
                assertThrows(IllegalArgumentException.class, () -> service.designer(5, 7, "BIENTOT", null, 9)).getMessage());

        when(plans.situationAgent(7, 5)).thenReturn(Map.of("poste_existe", true, "actif", true, "titulaire", false));
        service.designer(5, 7, "en_preparation", " Former à la paie avant 2027 ", 9);
        org.mockito.Mockito.verify(plans).enregistrer(5, 7, "EN_PREPARATION", "Former à la paie avant 2027", 9);
    }

    @Test
    void lHorizonEstBorne() {
        assertThrows(IllegalArgumentException.class, () -> service.tableau(0));
        assertThrows(IllegalArgumentException.class, () -> service.tableau(11));
    }
}

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
    private final ReleveService service = new ReleveService(postes);
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
    void lHorizonEstBorne() {
        assertThrows(IllegalArgumentException.class, () -> service.tableau(0));
        assertThrows(IllegalArgumentException.class, () -> service.tableau(11));
    }
}

package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.PosteRepository;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PosteServiceTests {
    private final PosteRepository repository = mock(PosteRepository.class);
    private final PosteService service = new PosteService(repository);

    private static Map<String, Object> poste(int id, int places) {
        return Map.of("id", id, "intitule", "Poste " + id, "nombre_places", places);
    }

    private static Map<String, Object> occupant(int posteId, int agentId, LocalDate droit) {
        Map<String, Object> ligne = new HashMap<>(Map.of("poste_id", posteId, "id", agentId, "nom", "AGENT"));
        ligne.put("depart_demande", null);
        ligne.put("retraite_possible", Date.valueOf(droit));
        return ligne;
    }

    @Test
    void unPosteEstLibreCompletOuSeLibereSelonSesPlacesEtSesDeparts() {
        LocalDate loin = LocalDate.now().plusYears(10);
        LocalDate bientot = LocalDate.now().plusMonths(3);
        when(repository.postesAvecPlaces(null, null)).thenReturn(List.of(poste(1, 2), poste(2, 1), poste(3, 1)));
        when(repository.occupants(null, null)).thenReturn(List.of(
                occupant(1, 10, loin), occupant(2, 20, loin), occupant(3, 30, bientot)));

        List<Map<String, Object>> etat = service.etatDesPostes(null, null);

        assertEquals("LIBRE", etat.get(0).get("statut"));
        assertEquals(1, etat.get(0).get("libres"));
        assertEquals("COMPLET", etat.get(1).get("statut"));
        assertEquals("SE_LIBERE", etat.get(2).get("statut"));
        assertEquals(bientot, etat.get(2).get("se_libere_le"));
    }

    @Test
    void unPosteGardeAuMoinsAutantDePlacesQueDOccupants() {
        when(repository.findById(1L)).thenReturn(new HashMap<>(Map.of("id", 1, "nombre_places", 2)));
        when(repository.postesAvecPlaces(null, null)).thenReturn(List.of(poste(1, 2)));
        when(repository.occupants(null, null)).thenReturn(List.of(
                occupant(1, 10, LocalDate.now().plusYears(9)), occupant(1, 11, LocalDate.now().plusYears(9))));

        assertEquals("2 agents occupent ce poste : il lui faut au moins 2 places.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.updatePoste(1L, "Poste", null, 3L, 1)).getMessage());
    }
}

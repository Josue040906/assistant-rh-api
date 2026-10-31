package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.OrganisationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrganisationServiceTests {

    private final OrganisationRepository repository = mock(OrganisationRepository.class);
    private final OrganisationService service = new OrganisationService(repository);

    @Test
    void listeLesDirectionsDepuisLeRepository() {
        List<Map<String, Object>> directions = List.of(Map.of("id", 1, "nom", "Direction A"));
        when(repository.listerDirections("Finances")).thenReturn(directions);

        Object resultat = service.executer("LIST_DIRECTIONS", null, null, null, "Finances");

        assertSame(directions, resultat);
        verify(repository).listerDirections("Finances");
    }

    @Test
    void exigeUnIdentifiantPourUneDirectionPrecise() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.executer("GET_DIRECTION", null, null, null, null)
        );
        verifyNoInteractions(repository);
    }

    @Test
    void signaleUneDirectionIntrouvable() {
        when(repository.obtenirDirection(999L)).thenReturn(null);

        assertThrows(
                OrganisationService.RessourceOrganisationIntrouvableException.class,
                () -> service.executer("GET_DIRECTION", 999L, null, null, null)
        );
    }

    @Test
    void refuseDeFabriquerUneHierarchieIndividuelleNonPresenteEnBase() {
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> service.executer("GET_EMPLOYEE_SUPERVISION", null, null, null, null)
        );
        assertTrue(exception.getMessage().contains("relation explicite"));
    }

    @Test
    void compteLesPostesEnTransmettantLesFiltresDirectionEtService() {
        when(repository.compterPostes(3L, null)).thenReturn(8L);

        Object resultat = service.executer("COUNT_POSTES", null, 3L, null, null);

        assertEquals(8L, ((Map<?, ?>) resultat).get("nombre_postes"));
        verify(repository).compterPostes(3L, null);
    }

    @Test
    void rejetteUneOperationInconnue() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.executer("UNKNOWN", null, null, null, null)
        );
    }
}

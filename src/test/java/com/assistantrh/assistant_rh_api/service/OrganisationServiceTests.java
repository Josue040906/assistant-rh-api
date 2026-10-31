package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.controller.OrganisationController.ConsultationRequest;
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
    void listeLesDirections() {
        when(repository.directions(null)).thenReturn(List.of(Map.of("id", 1L, "nom", "Direction financière")));
        Map<String,Object> result = service.consulter(
                new ConsultationRequest("LIST_DIRECTIONS", null, null, null, null, null));
        assertEquals("LIST_DIRECTIONS", result.get("operation"));
        assertEquals(1, result.get("total"));
        verify(repository).directions(null);
    }

    @Test
    void refuseUneOperationInconnue() {
        assertThrows(IllegalArgumentException.class, () -> service.consulter(
                new ConsultationRequest("DROP_TABLE", null, null, null, null, null)));
        verifyNoInteractions(repository);
    }

    @Test
    void exigeUnIdentifiantPourUneConsultationUnitaire() {
        assertThrows(IllegalArgumentException.class, () -> service.consulter(
                new ConsultationRequest("GET_DIRECTION", null, null, null, null, null)));
        verifyNoInteractions(repository);
    }

    @Test
    void conserveTousLesTitulairesDUnPoste() {
        when(repository.poste(1L)).thenReturn(Map.of("id", 1L, "intitule", "Chef de service"));
        when(repository.titulaires(1L)).thenReturn(List.of(
                Map.of("matricule", "A1", "nom", "RAKOTO"),
                Map.of("matricule", "A2", "nom", "RABE")));
        Map<String,Object> result = service.consulter(
                new ConsultationRequest("GET_POSTE", null, null, 1L, null, null));
        assertEquals(2, ((List<?>) ((Map<?,?>) result.get("data")).get("titulaires")).size());
    }

    @Test
    void construitLaHierarchieStructurelleSansInventerDeLienIndividuel() {
        when(repository.hierarchy(null, null, true)).thenReturn(List.of(
                Map.of("direction_id", 1L, "direction", "Direction A",
                        "service_id", 2L, "service", "Service A", "code_service", "SA",
                        "poste_id", 3L, "poste", "Chef de service",
                        "nombre_titulaires_actuels", 2L),
                Map.of("direction_id", 1L, "direction", "Direction A",
                        "service_id", 2L, "service", "Service A", "code_service", "SA",
                        "poste_id", 4L, "poste", "Agent",
                        "nombre_titulaires_actuels", 1L)));
        Map<String,Object> result = service.consulter(
                new ConsultationRequest("GET_HIERARCHY", null, null, null, null, null));
        Map<?,?> direction = (Map<?,?>) ((List<?>) result.get("data")).get(0);
        Map<?,?> serviceData = (Map<?,?>) ((List<?>) direction.get("services")).get(0);
        assertEquals(2, ((List<?>) serviceData.get("postes")).size());
    }

    @Test
    void neSupportePasUneSupervisionIndividuelleAbsenteDuModele() {
        assertThrows(IllegalArgumentException.class, () -> service.consulter(
                new ConsultationRequest("GET_EMPLOYEE_SUPERVISION", null, null, null, null, null)));
        verifyNoInteractions(repository);
    }
}

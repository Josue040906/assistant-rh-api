package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.AssistantOrganizationRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantOrganizationServiceTests {
    private final AssistantOrganizationRepository repository =
            mock(AssistantOrganizationRepository.class);
    private final AssistantOrganizationService service =
            new AssistantOrganizationService(repository);

    @Test
    void returnsTheRealOrganizationHierarchyAndDescriptions() {
        when(repository.search("Finances", "DIRECTION", 101))
                .thenReturn(List.of(
                        row(1L, "Direction financière", "Description direction",
                                2L, "SG", "Secrétariat général",
                                "Description service", 3L, "Gestionnaire",
                                "Description poste"),
                        row(1L, "Direction financière", "Description direction",
                                2L, "SG", "Secrétariat général",
                                "Description service", 4L, "Comptable",
                                null)
                ));

        AssistantOrganizationService.OrganizationResponse result =
                service.search("Finances", "direction");

        assertEquals("DIRECTION", result.scope());
        assertFalse(result.truncated());
        assertEquals(1, result.directions().size());
        assertEquals("Direction financière", result.directions().get(0).nom());
        assertEquals(1, result.directions().get(0).services().size());
        assertEquals(2, result.directions().get(0).services().get(0).postes().size());
        assertEquals(
                "Description poste",
                result.directions().get(0).services().get(0).postes().get(0).description()
        );
        verify(repository).search("Finances", "DIRECTION", 101);
    }

    @Test
    void validatesSearchLengthAndAllowedScope() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.search("x", null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.search("Finances", "EMPLOYEE")
        );
    }

    private Map<String, Object> row(
            Long directionId,
            String direction,
            String directionDescription,
            Long serviceId,
            String serviceCode,
            String service,
            String serviceDescription,
            Long posteId,
            String poste,
            String posteDescription
    ) {
        Map<String, Object> row = new HashMap<>();
        row.put("direction_id", directionId);
        row.put("direction", direction);
        row.put("direction_description", directionDescription);
        row.put("service_id", serviceId);
        row.put("service_code", serviceCode);
        row.put("service", service);
        row.put("service_description", serviceDescription);
        row.put("poste_id", posteId);
        row.put("poste", poste);
        row.put("poste_description", posteDescription);
        return row;
    }
}

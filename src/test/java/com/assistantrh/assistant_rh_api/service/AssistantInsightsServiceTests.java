package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.AssistantInsightsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AssistantInsightsServiceTests {

    private final AssistantInsightsRepository repository =
            mock(AssistantInsightsRepository.class);
    private final UtilisateurService utilisateurService =
            mock(UtilisateurService.class);
    private final AssistantInsightsService service =
            new AssistantInsightsService(repository, utilisateurService);

    @Test
    void refuseLesComptesSansAccesSpersAuxDonneesTransversales() {
        when(utilisateurService.estAgentSpersActif(7)).thenReturn(false);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.highestDegreeEmployees(7)
        );

        assertEquals(403, error.getStatusCode().value());
        verifyNoInteractions(repository);
    }

    @Test
    void refuseUneLimiteAffectationsHorsPlage() {
        when(utilisateurService.estAgentSpersActif(7)).thenReturn(true);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.recentAssignments(7, 51)
        );

        assertEquals(400, error.getStatusCode().value());
        verifyNoInteractions(repository);
    }
}

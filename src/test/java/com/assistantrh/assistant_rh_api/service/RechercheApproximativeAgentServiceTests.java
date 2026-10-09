package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.RechercheApproximativeAgentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RechercheApproximativeAgentServiceTests {

    private final RechercheApproximativeAgentRepository repository =
            mock(RechercheApproximativeAgentRepository.class);
    private final RechercheApproximativeAgentService service =
            new RechercheApproximativeAgentService(repository);

    @Test
    void neRecherchePasUneRequeteVide() {
        assertEquals(List.of(), service.rechercherAgentsApproximatifs("  "));
        verify(repository, never()).rechercher("  ");
    }

    @Test
    void transmetLaRequeteEtRetourneLesCandidatsDuRepository() {
        List<Map<String, Object>> candidats =
                List.of(Map.of("matricule", "AG-42", "score_correspondance", 0.82));
        when(repository.rechercher("RAKOTOARISOUA")).thenReturn(candidats);

        assertEquals(
                candidats,
                service.rechercherAgentsApproximatifs(" RAKOTOARISOUA ")
        );
        verify(repository).rechercher("RAKOTOARISOUA");
    }
}

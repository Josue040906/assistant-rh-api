package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.RechercheAgentService;
import com.assistantrh.assistant_rh_api.service.RechercheApproximativeAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RechercheAgentControllerTests {

    private final RechercheAgentService rechercheAgentService =
            mock(RechercheAgentService.class);
    private final RechercheApproximativeAgentService
            rechercheApproximativeAgentService =
            mock(RechercheApproximativeAgentService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new RechercheAgentController(
                    rechercheAgentService,
                    rechercheApproximativeAgentService
            ))
            .build();

    @Test
    void exposeLaRechercheApproximativeAvecLesScores() throws Exception {
        when(rechercheApproximativeAgentService
                .rechercherAgentsApproximatifs("RAKOTOARISOUA"))
                .thenReturn(List.of(Map.of(
                        "nom", "RAKOTOARISOA",
                        "prenom", "Faly",
                        "score_correspondance", 0.72
                )));

        mockMvc.perform(get("/api/recherche/agents/approximative")
                        .param("query", "RAKOTOARISOUA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("RAKOTOARISOUA"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.agents[0].nom").value("RAKOTOARISOA"))
                .andExpect(jsonPath("$.agents[0].score_correspondance").value(0.72));

        verify(rechercheApproximativeAgentService)
                .rechercherAgentsApproximatifs("RAKOTOARISOUA");
    }
}

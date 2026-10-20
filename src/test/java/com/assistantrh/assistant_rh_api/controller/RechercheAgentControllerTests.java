package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.RechercheAgentService;
import com.assistantrh.assistant_rh_api.service.RechercheApproximativeAgentService;
import com.assistantrh.assistant_rh_api.service.RechercheStructureeAgentService;
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
    private final RechercheStructureeAgentService rechercheStructureeAgentService =
            mock(RechercheStructureeAgentService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new RechercheAgentController(
                    rechercheAgentService,
                    rechercheApproximativeAgentService,
                    rechercheStructureeAgentService
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

    @Test
    void exposeLaRechercheStructureePourLAssistant() throws Exception {
        when(rechercheStructureeAgentService.rechercher(
                "{\"filters\":[{\"field\":\"age\",\"operator\":\"LT\",\"value\":30}]}"
        )).thenReturn(new RechercheStructureeAgentService.ResultatRecherche(
                1,
                20,
                List.of(Map.of("nom", "RAKOTO", "age", 29))
        ));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/recherche/agents/criteres")
                        .contentType("application/json")
                        .content("""
                                {"criteriaJson":"{\\"filters\\":[{\\"field\\":\\"age\\",\\"operator\\":\\"LT\\",\\"value\\":30}]}"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.agents[0].nom").value("RAKOTO"));

        verify(rechercheStructureeAgentService).rechercher(
                "{\"filters\":[{\"field\":\"age\",\"operator\":\"LT\",\"value\":30}]}"
        );
    }
}

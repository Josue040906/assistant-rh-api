package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.dto.AgentRechercheDto;
import com.assistantrh.assistant_rh_api.service.RechercheAgentService;
import com.assistantrh.assistant_rh_api.service.RechercheAgentAvanceeService;
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
    private final RechercheAgentAvanceeService rechercheAgentAvanceeService =
            mock(RechercheAgentAvanceeService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new RechercheAgentController(
                    rechercheAgentService,
                    rechercheApproximativeAgentService,
                    rechercheStructureeAgentService,
                    rechercheAgentAvanceeService
            ))
            .build();

    @Test
    void exposeLeMoteurAvanceAvecUnAstEtUneProjectionMinimaliste() throws Exception {
        String ast = """
                {"must":[{"field":"age","operator":"BETWEEN","value":25,"upperValue":35}],
                 "should":[{"field":"personne","operator":"FUZZY_PERSON","value":"Josié"}],
                 "must_not":[{"field":"service","operator":"CONTAINS","value":"Personnel"}],
                 "minimum_should_match":0,"limit":10}
                """.replaceAll("\\s+", "");
        when(rechercheAgentAvanceeService.rechercher(ast, "LIST"))
                .thenReturn(new RechercheAgentAvanceeService.Resultat(
                        1, 10, List.of(Map.of(
                                "id", 29,
                                "nom", "TSIVOERY",
                                "prenom", "Josué Césaire",
                                "poste", "Gestionnaire",
                                "service", "Finances",
                                "direction", "Administration",
                                "score_correspondance", 0.91
                        )), 0, false
                ));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/recherche/agents/avancee")
                        .contentType("application/json")
                        .content("""
                                {"criteriaJson":"{\\"must\\":[{\\"field\\":\\"age\\",\\"operator\\":\\"BETWEEN\\",\\"value\\":25,\\"upperValue\\":35}],\\"should\\":[{\\"field\\":\\"personne\\",\\"operator\\":\\"FUZZY_PERSON\\",\\"value\\":\\"Josié\\"}],\\"must_not\\":[{\\"field\\":\\"service\\",\\"operator\\":\\"CONTAINS\\",\\"value\\":\\"Personnel\\"}],\\"minimum_should_match\\":0,\\"limit\\":10}"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.agents[0].id").value(29))
                .andExpect(jsonPath("$.agents[0].score_correspondance").value(0.91))
                .andExpect(jsonPath("$.agents[0].matricule").doesNotExist())
                .andExpect(jsonPath("$.agents[0].telephone").doesNotExist());

        verify(rechercheAgentAvanceeService).rechercher(ast, "LIST");
    }

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
                "{\"filters\":[{\"field\":\"age\",\"operator\":\"LT\",\"value\":30}]}", "LIST"
        )).thenReturn(new RechercheStructureeAgentService.ResultatRecherche(
                1,
                20,
                List.of(new AgentRechercheDto(
                        123, "123", "RAKOTO", "Faly", "M", 29,
                        java.time.LocalDate.parse("2020-01-01"),
                        "Gestionnaire", "SG", "Finances", "Administration",
                        "A", "Administrateur", "Fonctionnaire", "I", 2, 0.9
                )),
                0,
                false
        ));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/recherche/agents/criteres")
                        .contentType("application/json")
                        .content("""
                                {"criteriaJson":"{\\"filters\\":[{\\"field\\":\\"age\\",\\"operator\\":\\"LT\\",\\"value\\":30}]}"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.agents[0].nom").value("RAKOTO"))
                .andExpect(jsonPath("$.agents[0].code_service").value("SG"))
                .andExpect(jsonPath("$.agents[0].typeEmploi").value("Fonctionnaire"))
                .andExpect(jsonPath("$.agents[0].date_embauche").value("2020-01-01"))
                .andExpect(jsonPath("$.agents[0].score_correspondance").value(0.9))
                .andExpect(jsonPath("$.agents[0].id").value(123))
                .andExpect(jsonPath("$.agents[0].date_naissance").doesNotExist())
                .andExpect(jsonPath("$.agents[0].adresse").doesNotExist())
                .andExpect(jsonPath("$.agents[0].photo").doesNotExist());

        verify(rechercheStructureeAgentService).rechercher(
                "{\"filters\":[{\"field\":\"age\",\"operator\":\"LT\",\"value\":30}]}", "LIST"
        );
    }

    @Test
    void retourneUneListeVideQuandLaRechercheStructureeNeTrouveRien()
            throws Exception {
        when(rechercheStructureeAgentService.rechercher(
                "{\"filters\":[{\"field\":\"age\",\"operator\":\"LT\",\"value\":18}]}", "LIST"
        )).thenReturn(new RechercheStructureeAgentService.ResultatRecherche(
                0, 20, List.of(), 0, false
        ));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/recherche/agents/criteres")
                        .contentType("application/json")
                        .content("""
                                {"criteriaJson":"{\\"filters\\":[{\\"field\\":\\"age\\",\\"operator\\":\\"LT\\",\\"value\\":18}]}"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.agents").isEmpty());
    }
}

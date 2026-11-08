package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.RechercheAgentAvanceeService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RechercheAgentControllerTests {

    private final RechercheAgentAvanceeService rechercheAgentAvanceeService =
            mock(RechercheAgentAvanceeService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new RechercheAgentController(rechercheAgentAvanceeService))
            .build();

    @Test
    void exposeLeMoteurAvanceAvecUnAstEtUneProjectionMinimaliste() throws Exception {
        String ast = """
                {"must":[{"field":"age","operator":"BETWEEN","value":25,"upperValue":35}],
                 "should":[{"field":"personne","operator":"FUZZY_PERSON","value":"Josié"}],
                 "must_not":[{"field":"service","operator":"CONTAINS","value":"Personnel"}],
                 "minimum_should_match":0,"limit":10}
                """.replaceAll("\\s+", "");
        when(rechercheAgentAvanceeService.rechercher(ast, null))
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

        verify(rechercheAgentAvanceeService).rechercher(ast, null);
    }

}

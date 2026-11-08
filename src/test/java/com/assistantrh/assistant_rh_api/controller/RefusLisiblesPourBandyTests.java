package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.ProfilAgentAmbiguException;
import com.assistantrh.assistant_rh_api.service.ProfilAgentService;
import com.assistantrh.assistant_rh_api.service.RechercheAgentAvanceeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L'assistant explique les refus de l'API à partir de leur statut et de leur
 * message : chaque refus doit donc porter un message lisible.
 */
class RefusLisiblesPourBandyTests {

    private final ProfilAgentService profilAgentService = mock(ProfilAgentService.class);
    private final MockMvc profil = MockMvcBuilders
            .standaloneSetup(new ProfilAgentController(profilAgentService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void agentIntrouvableRepond404AvecUnMessage() throws Exception {
        when(profilAgentService.obtenirProfilAgent("Jean")).thenReturn(Optional.empty());

        profil.perform(get("/api/profil/agent").param("query", "Jean"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AGENT_INTROUVABLE"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void homonymesRepondent409AvecLesCandidatsSansEnChoisirUn() throws Exception {
        when(profilAgentService.obtenirProfilAgent("Jean RAKOTO")).thenThrow(
                new ProfilAgentAmbiguException("Jean RAKOTO", List.of(
                        Map.of("matricule", "FIC-0101", "nom", "RAKOTO", "prenom", "Jean"),
                        Map.of("matricule", "FIC-0201", "nom", "RAKOTO", "prenom", "Jean")
                ))
        );

        profil.perform(get("/api/profil/agent").param("query", "Jean RAKOTO"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AGENT_AMBIGU"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.candidats.length()").value(2))
                .andExpect(jsonPath("$.candidats[1].matricule").value("FIC-0201"));
    }

    @Test
    void rechercheSansCriterePositifRepond400AvantToutAccesALaBase() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        MockMvc recherche = MockMvcBuilders
                .standaloneSetup(new RechercheAgentController(
                        new RechercheAgentAvanceeService(jdbc, new ObjectMapper())
                ))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        for (String criteria : List.of(
                "{\"must\":[],\"should\":[],\"must_not\":[],\"minimum_should_match\":0}",
                "{\"must\":[],\"should\":[],\"must_not\":[{\"field\":\"sexe\",\"operator\":\"EQ\",\"value\":\"F\"}],\"minimum_should_match\":0}"
        )) {
            recherche.perform(post("/api/recherche/agents/avancee")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(new ObjectMapper().writeValueAsString(
                                    Map.of("criteriaJson", criteria, "resultMode", "LIST")
                            )))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Au moins un critère positif dans must ou should est obligatoire."
                    ));
        }
        verifyNoInteractions(jdbc);
    }
}

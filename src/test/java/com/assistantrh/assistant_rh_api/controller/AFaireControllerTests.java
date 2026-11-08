package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.AFaireService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AFaireControllerTests {
    private final AFaireService service = mock(AFaireService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AFaireController(service))
            .build();

    @Test
    void renvoieLaListeDeLUtilisateurAuthentifie() throws Exception {
        when(service.pour(42)).thenReturn(new AFaireService.AFaire(List.of(
                new AFaireService.Element("documents_brouillon", "Documents en brouillon", 6, "/documents")
        )));

        mockMvc.perform(get("/api/a-faire").requestAttr("authenticatedUserId", 42))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elements[0].code").value("documents_brouillon"))
                .andExpect(jsonPath("$.elements[0].libelle").value("Documents en brouillon"))
                .andExpect(jsonPath("$.elements[0].nombre").value(6))
                .andExpect(jsonPath("$.elements[0].route").value("/documents"));
    }
}

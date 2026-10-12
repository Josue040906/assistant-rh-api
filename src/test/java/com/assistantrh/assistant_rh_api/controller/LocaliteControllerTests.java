package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.model.Localite;
import com.assistantrh.assistant_rh_api.service.LocaliteService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LocaliteControllerTests {

    private final LocaliteService service = mock(LocaliteService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new LocaliteController(service))
            .build();

    @Test
    void retourneLesRegionsSousFormeJson() throws Exception {
        when(service.regions()).thenReturn(List.of(
                new Localite(1L, "REGION", "Analamanga", null, "MG11", "HDX_OCHA")
        ));

        mockMvc.perform(get("/api/localites/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nom").value("Analamanga"))
                .andExpect(jsonPath("$[0].niveau").value("REGION"));

        verify(service).regions();
    }

    @Test
    void rechercheExposeLaPaginationEtLesResultats() throws Exception {
        Localite localite = new Localite(
                5L, "COMMUNE", "Antananarivo", 4L, "MG11101", "HDX_OCHA"
        );
        when(service.recherche("Anta", 0, 20)).thenReturn(
                new LocaliteService.PageResultat(0, 20, 1, List.of(localite))
        );

        mockMvc.perform(get("/api/localites/recherche")
                        .param("q", "Anta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.resultats[0].nom").value("Antananarivo"));

        verify(service).recherche("Anta", 0, 20);
    }
}

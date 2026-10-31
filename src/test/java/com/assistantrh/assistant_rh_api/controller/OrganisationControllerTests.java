package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.OrganisationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrganisationControllerTests {
    private final OrganisationService service = mock(OrganisationService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new OrganisationController(service)).build();

    @Test
    void exposeLaConsultationOrganisationnelle() throws Exception {
        when(service.consulter(new OrganisationController.ConsultationRequest(
                "LIST_DIRECTIONS", null, null, null, null, null)))
                .thenReturn(Map.of("operation", "LIST_DIRECTIONS", "total", 1,
                        "data", List.of(Map.of("id", 1, "nom", "Direction financière"))));

        mvc.perform(post("/api/organisation/consultation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"operation":"LIST_DIRECTIONS"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("LIST_DIRECTIONS"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].nom").value("Direction financière"));
    }
}

package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.dto.ProfilAgentPersonnelDto;
import com.assistantrh.assistant_rh_api.service.ProfilAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MonProfilAgentControllerTests {
    private final ProfilAgentService profilAgentService =
            mock(ProfilAgentService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new MonProfilAgentController(profilAgentService))
            .build();

    @Test
    void returnsTheProfileForTheAuthenticatedUserId() throws Exception {
        when(profilAgentService.obtenirMonProfilAgent(42)).thenReturn(
                new ProfilAgentPersonnelDto(
                        "AG-42",
                        "RAKOTO",
                        "Faly",
                        "faly@example.test",
                        LocalDate.parse("2018-01-01"),
                        "Fonctionnaire",
                        "A",
                        "Administrateur",
                        "Gestionnaire",
                        "Service du Personnel",
                        "Administration",
                        List.of()
                )
        );

        mockMvc.perform(get("/api/profil/agent/mon-profil")
                        .requestAttr("authenticatedUserId", 42))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("RAKOTO"))
                .andExpect(jsonPath("$.email").value("faly@example.test"))
                .andExpect(jsonPath("$.service").value("Service du Personnel"))
                .andExpect(jsonPath("$.cin").doesNotExist())
                .andExpect(jsonPath("$.telephone").doesNotExist())
                .andExpect(jsonPath("$.adresse").doesNotExist());

        verify(profilAgentService).obtenirMonProfilAgent(42);
    }
}

package com.assistantrh.assistant_rh_api.repository;

import com.assistantrh.assistant_rh_api.service.RechercheStructureeAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class RechercheStructureeAgentRepositoryTests {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final RechercheStructureeAgentRepository repository =
            new RechercheStructureeAgentRepository(jdbcTemplate);

    @Test
    void construitUneRequeteParametreeSurLesCritereProfessionnelsEtPersonnels() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());

        repository.rechercher(
                List.of(
                        new RechercheStructureeAgentService.Filtre(
                                "age",
                                RechercheStructureeAgentService.Operateur.LT,
                                30,
                                null
                        ),
                        new RechercheStructureeAgentService.Filtre(
                                "direction",
                                RechercheStructureeAgentService.Operateur.CONTAINS,
                                "finances",
                                null
                        ),
                        new RechercheStructureeAgentService.Filtre(
                                "date_embauche",
                                RechercheStructureeAgentService.Operateur.BEFORE,
                                "2020-01-01",
                                null
                        )
                ),
                15,
                30,
                "age",
                "ASC"
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argumentsCaptor =
                ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(
                sqlCaptor.capture(),
                argumentsCaptor.capture()
        );

        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("EXTRACT(YEAR FROM age(CURRENT_DATE, e.date_naissance))"));
        assertTrue(sql.contains("d.nom"));
        assertTrue(sql.contains("e.date_embauche < ?"));
        assertTrue(sql.contains("FROM historique_carriere hc"));
        org.junit.jupiter.api.Assertions.assertFalse(sql.contains("grade_carriere"));
        org.junit.jupiter.api.Assertions.assertFalse(sql.contains("e.grade_id"));
        assertTrue(sql.contains("LIMIT ?"));
        assertTrue(sql.contains("OFFSET ?"));
        Object[] arguments = argumentsCaptor.getValue();
        assertEquals(30, arguments[0]);
        assertEquals("%finances%", arguments[1]);
        assertEquals(LocalDate.parse("2020-01-01"), arguments[2]);
        assertEquals(16, arguments[3]);
        assertEquals(30, arguments[4]);
    }

    @Test
    void mappeLesColonnesAutoriseesVersLeDtoStableDuContrat() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(
                        Map.of(
                                "matricule", "123",
                                "nom", "RAKOTO",
                                "age", 29,
                                "date_embauche", java.sql.Date.valueOf("2020-01-01"),
                                "score_correspondance", 0.9
                        ),
                        Map.of("matricule", "456", "nom", "RABE")
                ));

        var agents = repository.rechercher(
                List.of(new RechercheStructureeAgentService.Filtre(
                        "nom",
                        RechercheStructureeAgentService.Operateur.EQ,
                        "rakoto",
                        null
                )),
                1,
                0,
                null,
                "ASC"
        );

        assertEquals(1, agents.agents().size());
        assertEquals("123", agents.agents().get(0).matricule());
        assertEquals("RAKOTO", agents.agents().get(0).nom());
        assertEquals(29, agents.agents().get(0).age());
        assertEquals(LocalDate.parse("2020-01-01"), agents.agents().get(0).dateEmbauche());
        assertEquals(0.9, agents.agents().get(0).scoreCorrespondance());
        assertTrue(agents.hasMore());
    }

    @Test
    void classeLesMatchesFlousAvantLesFiltresEtSesValeursRestentParametrees() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());

        repository.rechercher(
                List.of(new RechercheStructureeAgentService.Filtre(
                        "nom",
                        RechercheStructureeAgentService.Operateur.FUZZY,
                        "rakoto",
                        null
                )),
                20,
                0,
                "nom",
                "ASC"
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argumentsCaptor =
                ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(
                sqlCaptor.capture(),
                argumentsCaptor.capture()
        );
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("similarity("));
        assertTrue(sql.contains(" % ? AND similarity("));
        assertTrue(sql.contains("score_correspondance DESC"));
        assertTrue(sql.contains("WHERE"));
        assertTrue(sql.substring(sql.indexOf("WHERE")).contains("similarity("));
        assertEquals(
                List.of("rakoto", "rakoto", "rakoto", 0.42, 21, 0),
                List.of(argumentsCaptor.getValue())
        );
    }
}

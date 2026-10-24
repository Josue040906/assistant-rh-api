package com.assistantrh.assistant_rh_api.repository;

import com.assistantrh.assistant_rh_api.observability.PerformanceTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantInsightsRepositoryTests {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PerformanceTelemetry performanceTelemetry =
            mock(PerformanceTelemetry.class);
    private final AssistantInsightsRepository repository =
            new AssistantInsightsRepository(jdbcTemplate, performanceTelemetry);

    @Test
    void retourneDesCompteursPourLesReferentielsRH() {
        Map<String, Object> counts = Map.of("agents", 12L, "postes", 4L);
        when(jdbcTemplate.queryForMap(anyString())).thenReturn(counts);

        assertEquals(counts, repository.organizationOverview());
        verify(performanceTelemetry).databaseQuery(
                eq("assistant_insights_organization"),
                anyLong(),
                eq(true)
        );
        verify(jdbcTemplate).queryForMap(
                org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("FROM employe")
                                && sql.contains("FROM direction")
                                && sql.contains("FROM echelon")
                                && sql.contains("FROM niveau_diplome")
                )
        );
    }

    @Test
    void compteLesAgentsSelonLeurAffectationEnCoursAvecDesParametresLies() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("poste", "Gestionnaire", "agents", 3L)));

        List<Map<String, Object>> result =
                repository.employeesByPosition("Gestionnaire");

        assertEquals(3L, result.get(0).get("agents"));
        org.mockito.ArgumentCaptor<String> sql =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object[]> arguments =
                org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(sql.capture(), arguments.capture());
        assertTrue(sql.getValue().contains("af.date_fin IS NULL"));
        assertTrue(sql.getValue().contains("af.date_debut <= CURRENT_DATE"));
        assertTrue(sql.getValue().contains("DISTINCT ON (af.employe_id)"));
        assertEquals("Gestionnaire", arguments.getValue()[0]);
        assertEquals("Gestionnaire", arguments.getValue()[1]);
    }

    @Test
    void classeLesDiplomesParOrdreAcademiqueConfigureSansDevinerLeLibelle() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        repository.highestDegreeEmployees();

        verify(jdbcTemplate).queryForList(
                org.mockito.ArgumentMatchers.argThat(sql ->
                        sql.contains("MAX(n.ordre_academique)")
                                && sql.contains("n.ordre_academique")
                                && sql.contains("FROM employe_diplome")
                )
        );
    }

    @Test
    void borneLesAffectationsRecentesAuNombreDemande() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());

        repository.recentAssignments(10);

        org.mockito.ArgumentCaptor<String> sql =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object[]> arguments =
                org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(sql.capture(), arguments.capture());
        assertTrue(sql.getValue().contains("ORDER BY af.date_debut DESC"));
        assertTrue(sql.getValue().contains("LIMIT ?"));
        assertEquals(10, arguments.getValue()[0]);
    }
}

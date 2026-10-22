package com.assistantrh.assistant_rh_api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class RechercheApproximativeAgentRepositoryTests {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final RechercheApproximativeAgentRepository repository =
            new RechercheApproximativeAgentRepository(jdbcTemplate);

    @Test
    void retourneLesDeuxMeilleursCandidatsEtAppliqueLesFiltresSansSQLDynamique() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());

        repository.rechercher(
                "  Éléonore Rakoto  ",
                "Finances",
                "Gestionnaire",
                "Direction générale"
        );

        ArgumentCaptor<String> sqlCaptor =
                ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argumentsCaptor =
                ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(
                sqlCaptor.capture(),
                argumentsCaptor.capture()
        );

        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("similarity("));
        assertTrue(sql.contains("word_similarity("));
        assertTrue(sql.contains("levenshtein("));
        assertTrue(sql.contains("ROW_NUMBER() OVER"));
        assertTrue(sql.contains("candidate_rank <= 2"));
        assertTrue(sql.contains("LIMIT ?"));
        assertTrue(sql.contains("score_correspondance >= ?"));
        assertTrue(sql.contains("service_filter"));
        assertTrue(sql.contains("poste_filter"));
        assertTrue(sql.contains("direction_filter"));
        assertTrue(sql.contains("matricule_exact"));
        assertTrue(sql.contains("char_length("));
        assertTrue(sql.contains("LEFT JOIN affectation af"));
        assertTrue(sql.contains("SELECT DISTINCT ON (e.id)"));
        assertTrue(sql.contains("af.date_fin IS NULL"));
        org.junit.jupiter.api.Assertions.assertFalse(
                sql.contains("LEFT JOIN LATERAL (")
        );

        Object[] arguments = argumentsCaptor.getValue();
        assertEquals("Éléonore Rakoto", arguments[0]);
        assertEquals("eleonore rakoto", arguments[1]);
        assertEquals("Finances", arguments[2]);
        assertEquals("Gestionnaire", arguments[3]);
        assertEquals("Direction générale", arguments[4]);
        assertEquals(0.55, arguments[5]);
        assertEquals(10, arguments[6]);
    }

    @Test
    void accepteUneRechercheSansFiltresAdditionnels() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());

        repository.rechercher("Rakoto");

        ArgumentCaptor<Object[]> argumentsCaptor =
                ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(anyString(), argumentsCaptor.capture());
        Object[] arguments = argumentsCaptor.getValue();
        assertEquals("Rakoto", arguments[0]);
        assertEquals("rakoto", arguments[1]);
        assertNull(arguments[2]);
        assertNull(arguments[3]);
        assertNull(arguments[4]);
        assertEquals(0.55, arguments[5]);
        assertEquals(10, arguments[6]);
    }
}

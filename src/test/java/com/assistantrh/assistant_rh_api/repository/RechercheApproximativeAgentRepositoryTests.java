package com.assistantrh.assistant_rh_api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

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

class RechercheApproximativeAgentRepositoryTests {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final RechercheApproximativeAgentRepository repository =
            new RechercheApproximativeAgentRepository(jdbcTemplate);

    @Test
    void utiliseUneRequeteParametreeAvecScoreSeuilEtLimite() {
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of());

        repository.rechercher("  Éléonore Rakoto  ");

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
        assertTrue(sql.contains("LIMIT ?"));
        assertTrue(sql.contains("score_correspondance >= ?"));
        assertTrue(sql.contains("matricule_exact"));
        assertTrue(sql.contains("char_length("));

        Object[] arguments = argumentsCaptor.getValue();
        assertEquals("Éléonore Rakoto", arguments[0]);
        assertEquals("eleonore rakoto", arguments[1]);
        assertEquals(0.55, arguments[2]);
        assertEquals(10, arguments[3]);
    }
}

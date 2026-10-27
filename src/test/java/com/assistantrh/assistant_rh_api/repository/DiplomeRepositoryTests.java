package com.assistantrh.assistant_rh_api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiplomeRepositoryTests {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DiplomeRepository repository = new DiplomeRepository(jdbcTemplate);

    @Test
    void listeLesNiveauxEtLeursCategoriesSansTableDeRegles() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.<Map<String, Object>>of());

        repository.findCorrespondancesCategorie();

        org.mockito.ArgumentCaptor<String> sql =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture());
        String query = sql.getValue().toLowerCase();

        assertTrue(query.contains("from niveau_diplome"));
        assertTrue(query.contains("left join categorie"));
        assertTrue(query.contains("left join corps"));
        assertTrue(!query.contains("regle_reclassement"));
        assertTrue(query.contains("ordre_academique"));
    }
}

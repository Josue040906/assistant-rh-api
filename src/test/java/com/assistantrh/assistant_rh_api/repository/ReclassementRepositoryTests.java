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

class ReclassementRepositoryTests {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ReclassementRepository repository = new ReclassementRepository(jdbcTemplate);

    @Test
    void enregistreLeChangementDansLesColonnesExistantes() {
        repository.enregistrer(9, 101, 2);

        org.mockito.ArgumentCaptor<String> sql =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object[]> params =
                org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sql.capture(), params.capture());

        assertTrue(sql.getValue().contains("INSERT INTO reclassement"));
        assertTrue(sql.getValue().contains("(employe_id, categorie_id, id_activite)"));
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                new Object[]{9, 2, 101}, params.getValue()
        );
    }

    @Test
    void listeLesReclassementsAvecAgentAuteurEtHorodatage() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.<Map<String, Object>>of());

        repository.historiqueGlobal();

        org.mockito.ArgumentCaptor<String> sql =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture());
        String query = sql.getValue().toLowerCase();

        assertTrue(query.contains("from reclassement r"));
        assertTrue(query.contains("join employe e"));
        assertTrue(query.contains("join activite a"));
        assertTrue(query.contains("join categorie c"));
        assertTrue(query.contains("a.date_heure"));
        assertTrue(!query.contains("regle_reclassement"));
    }
}

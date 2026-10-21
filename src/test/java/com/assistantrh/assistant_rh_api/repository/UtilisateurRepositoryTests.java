package com.assistantrh.assistant_rh_api.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class UtilisateurRepositoryTests {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final UtilisateurRepository repository =
            new UtilisateurRepository(jdbcTemplate);

    @Test
    void verifieLeServiceDeLaffectationCourantePourAutoriserLeCompte() {
        repository.estAgentSpersActif(42);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
                sqlCaptor.capture(),
                eq(Boolean.class),
                any(Object[].class)
        );
        String sql = sqlCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertTrue(
                sql.contains("af.date_debut <= CURRENT_DATE")
        );
        org.junit.jupiter.api.Assertions.assertTrue(
                sql.contains("a.service_id = 2")
        );
    }
}

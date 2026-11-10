package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Optional;

@Repository
public class RetraiteRepository {
    private final JdbcTemplate jdbcTemplate;

    public RetraiteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<LocalDate> dateRetraite(Integer employeId) {
        return jdbcTemplate.queryForList(
                "SELECT date_retraite FROM employe WHERE id = ? AND date_retraite IS NOT NULL",
                LocalDate.class, employeId).stream().findFirst();
    }

    /** L'agent part à la retraite ; son affectation et sa période de carrière en cours se ferment. */
    public int enregistrerRetraite(Integer employeId, LocalDate dateEffet) {
        Date date = Date.valueOf(dateEffet);
        jdbcTemplate.update("""
                UPDATE affectation SET date_fin = GREATEST(date_debut, ?::date - 1)
                WHERE employe_id = ? AND date_fin IS NULL
                """, date, employeId);
        jdbcTemplate.update("""
                UPDATE historique_carriere SET date_fin = GREATEST(date_debut, ?::date - 1)
                WHERE employe_id = ? AND date_fin IS NULL
                """, date, employeId);
        return jdbcTemplate.update(
                "UPDATE employe SET date_retraite = ? WHERE id = ? AND date_retraite IS NULL",
                date, employeId);
    }
}

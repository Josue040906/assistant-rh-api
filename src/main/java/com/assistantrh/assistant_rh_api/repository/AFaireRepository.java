package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Les comptages qui alimentent la liste « À faire ». */
@Repository
public class AFaireRepository {
    private final JdbcTemplate jdbcTemplate;

    public AFaireRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int comptesEnAttente() {
        return compter("SELECT COUNT(*) FROM utilisateur WHERE statut_compte = 'EN_ATTENTE'");
    }

    public int documentsAuStatut(String statut) {
        return compter("SELECT COUNT(*) FROM document WHERE statut = ?", statut);
    }

    /** Un dossier est incomplet s'il manque une pièce d'identité ou de contact. */
    public int dossiersIncomplets() {
        return compter("""
                SELECT COUNT(*) FROM employe
                WHERE NULLIF(BTRIM(cin), '') IS NULL
                   OR NULLIF(BTRIM(telephone), '') IS NULL
                   OR NULLIF(BTRIM(adresse), '') IS NULL
                   OR date_naissance IS NULL
                   OR NULLIF(BTRIM(lieu_naissance), '') IS NULL
                   OR NULLIF(BTRIM(photo), '') IS NULL
                """);
    }

    private int compter(String sql, Object... arguments) {
        Integer nombre = jdbcTemplate.queryForObject(sql, Integer.class, arguments);
        return nombre == null ? 0 : nombre;
    }
}

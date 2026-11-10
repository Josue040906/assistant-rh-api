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

    /** Demandes créées dont la date d'effet est atteinte : un humain doit les appliquer. */
    public int demandesApplicables() {
        return compter("SELECT COUNT(*) FROM document WHERE statut = 'CREE' AND date_effet <= CURRENT_DATE");
    }

    public int dossiersIncomplets() {
        return compter("SELECT COUNT(*) FROM employe e WHERE "
                + EmployeRepository.DOSSIER_INCOMPLET);
    }

    private int compter(String sql, Object... arguments) {
        Integer nombre = jdbcTemplate.queryForObject(sql, Integer.class, arguments);
        return nombre == null ? 0 : nombre;
    }
}

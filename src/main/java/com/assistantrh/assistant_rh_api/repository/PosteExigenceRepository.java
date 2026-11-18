package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Les exigences d'un poste (langue, diplôme, compétence, formation) et ce qu'il faut pour les vérifier. */
@Repository
public class PosteExigenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public PosteExigenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> exigences(Long posteId) {
        return jdbcTemplate.queryForList("""
                SELECT x.id, x.type, x.valeur, x.niveau, x.indispensable,
                       n.libelle AS niveau_libelle, n.ordre_academique AS niveau_ordre
                FROM poste_exigence x
                LEFT JOIN niveau_diplome n ON x.type = 'DIPLOME' AND n.code = x.niveau
                WHERE x.poste_id = ?
                ORDER BY x.indispensable DESC, x.ordre, x.id
                """, posteId);
    }

    /** Remplace toutes les exigences du poste par cette liste (type, valeur, niveau, indispensable). */
    @Transactional
    public void remplacer(Long posteId, List<Map<String, Object>> exigences) {
        jdbcTemplate.update("DELETE FROM poste_exigence WHERE poste_id = ?", posteId);
        int ordre = 0;
        for (Map<String, Object> exigence : exigences) {
            jdbcTemplate.update("""
                    INSERT INTO poste_exigence (poste_id, type, valeur, niveau, indispensable, ordre)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, posteId, exigence.get("type"), exigence.get("valeur"), exigence.get("niveau"),
                    Boolean.TRUE.equals(exigence.get("indispensable")), ordre++);
        }
    }

    public boolean niveauDiplomeExiste(String code) {
        Integer nombre = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM niveau_diplome WHERE code = ?", Integer.class, code);
        return nombre != null && nombre > 0;
    }

    /** Les langues de ces agents (employe_id, langue, niveau). */
    public List<Map<String, Object>> langues(List<Integer> employeIds) {
        if (employeIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.queryForList("SELECT employe_id, langue, niveau FROM employe_langue WHERE employe_id IN ("
                + String.join(",", Collections.nCopies(employeIds.size(), "?")) + ")", employeIds.toArray());
    }
}

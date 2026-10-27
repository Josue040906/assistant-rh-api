package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Date;
import java.util.List;
import java.util.Map;

@Repository
public class ReclassementRepository {
    private final JdbcTemplate jdbcTemplate;
    public ReclassementRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public int enregistrer(Integer employeId, Integer activiteId, Integer diplomeId,
            Integer historiqueAvantId, Integer historiqueApresId,
            Integer categorieAvantId, Integer categorieApresId,
            Integer corpsAvantId, Integer corpsApresId, Date dateEffet) {
        return jdbcTemplate.update("""
            INSERT INTO reclassement (
                employe_id, id_activite, employe_diplome_id,
                historique_avant_id, historique_apres_id,
                categorie_avant_id, categorie_apres_id,
                corps_avant_id, corps_apres_id, date_effet
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, employeId, activiteId, diplomeId, historiqueAvantId, historiqueApresId,
                categorieAvantId, categorieApresId, corpsAvantId, corpsApresId, dateEffet);
    }

    public List<Map<String, Object>> historiqueEmploye(Integer employeId) {
        return jdbcTemplate.queryForList("""
            SELECT r.id, r.employe_id, r.id_activite, r.employe_diplome_id,
                   r.historique_avant_id, r.historique_apres_id,
                   r.categorie_avant_id, ca.code AS categorie_avant,
                   r.categorie_apres_id, cp.code AS categorie_apres,
                   r.corps_avant_id, r.corps_apres_id,
                   r.date_effet, r.date_creation,
                   a.acteur_id, a.description AS activite_description, a.date_heure
            FROM reclassement r
            JOIN activite a ON a.id = r.id_activite
            LEFT JOIN categorie ca ON ca.id = r.categorie_avant_id
            LEFT JOIN categorie cp ON cp.id = r.categorie_apres_id
            WHERE r.employe_id = ?
            ORDER BY r.date_effet DESC, r.id DESC
            """, employeId);
    }
}
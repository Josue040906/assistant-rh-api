package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Ce que le CV d'un agent montre au-delà de sa carrière : situation familiale,
 * langues, compétences et formations continues.
 */
@Repository
public class ProfilComplementaireRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProfilComplementaireRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> situationFamiliale(Integer employeId) {
        return jdbcTemplate.queryForList(
                "SELECT situation_matrimoniale, nombre_enfants FROM employe WHERE id = ?", employeId)
                .stream().findFirst().orElse(Map.of());
    }

    public List<Map<String, Object>> langues(Integer employeId) {
        return jdbcTemplate.queryForList("""
                SELECT langue, niveau FROM employe_langue
                WHERE employe_id = ?
                ORDER BY CASE niveau WHEN 'MATERNELLE' THEN 0 WHEN 'COURANT' THEN 1
                                     WHEN 'INTERMEDIAIRE' THEN 2 ELSE 3 END, langue
                """, employeId);
    }

    public List<String> competences(Integer employeId) {
        return jdbcTemplate.queryForList(
                "SELECT libelle FROM employe_competence WHERE employe_id = ? ORDER BY id", String.class, employeId);
    }

    public List<Map<String, Object>> formations(Integer employeId) {
        return jdbcTemplate.queryForList("""
                SELECT intitule, organisme, annee, duree_jours FROM employe_formation
                WHERE employe_id = ?
                ORDER BY annee DESC NULLS LAST, id DESC
                """, employeId);
    }

    /** Les compétences déjà saisies pour des agents, les plus fréquentes d'abord (suggestions). */
    public List<String> competencesConnues() {
        return jdbcTemplate.queryForList("""
                SELECT MIN(libelle) FROM employe_competence
                GROUP BY LOWER(libelle)
                ORDER BY COUNT(*) DESC, MIN(libelle)
                LIMIT 200
                """, String.class);
    }

    public int enregistrerSituationFamiliale(Integer employeId, String situation, Integer enfants) {
        return jdbcTemplate.update(
                "UPDATE employe SET situation_matrimoniale = ?, nombre_enfants = ? WHERE id = ?",
                situation, enfants, employeId);
    }

    /** Remplace les langues, compétences et formations de l'agent (dans la transaction de l'appelant). */
    public void remplacerListes(Integer employeId, List<Map<String, Object>> langues, List<String> competences,
                                List<Map<String, Object>> formations) {
        jdbcTemplate.update("DELETE FROM employe_langue WHERE employe_id = ?", employeId);
        jdbcTemplate.update("DELETE FROM employe_competence WHERE employe_id = ?", employeId);
        jdbcTemplate.update("DELETE FROM employe_formation WHERE employe_id = ?", employeId);
        for (Map<String, Object> langue : langues) {
            jdbcTemplate.update("INSERT INTO employe_langue (employe_id, langue, niveau) VALUES (?, ?, ?)",
                    employeId, langue.get("langue"), langue.get("niveau"));
        }
        for (String competence : competences) {
            jdbcTemplate.update("INSERT INTO employe_competence (employe_id, libelle) VALUES (?, ?)",
                    employeId, competence);
        }
        for (Map<String, Object> formation : formations) {
            jdbcTemplate.update("""
                    INSERT INTO employe_formation (employe_id, intitule, organisme, annee, duree_jours)
                    VALUES (?, ?, ?, ?, ?)
                    """, employeId, formation.get("intitule"), formation.get("organisme"),
                    formation.get("annee"), formation.get("dureeJours"));
        }
    }
}

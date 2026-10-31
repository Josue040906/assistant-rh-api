package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class OrganisationRepository {
    private final JdbcTemplate jdbc;

    public OrganisationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String,Object>> directions(String query) {
        String pattern = pattern(query);
        return jdbc.queryForList("""
            SELECT d.id, d.nom, d.description,
                   COUNT(DISTINCT s.id) AS nombre_services,
                   COUNT(DISTINCT a.employe_id) AS nombre_agents
            FROM direction d
            LEFT JOIN service s ON s.direction_id = d.id
            LEFT JOIN poste p ON p.service_id = s.id
            LEFT JOIN affectation a ON a.service_id = s.id AND a.poste_id = p.id
                 AND a.date_debut <= CURRENT_DATE
                 AND (a.date_fin IS NULL OR a.date_fin >= CURRENT_DATE)
            WHERE (? IS NULL OR d.nom ILIKE ? OR COALESCE(d.description,'') ILIKE ?)
            GROUP BY d.id, d.nom, d.description
            ORDER BY d.nom
            """, nullable(query), pattern, pattern);
    }

    public Map<String,Object> direction(Long id) {
        List<Map<String,Object>> rows = jdbc.queryForList("""
            SELECT d.id, d.nom, d.description,
                   COUNT(DISTINCT s.id) AS nombre_services,
                   COUNT(DISTINCT a.employe_id) AS nombre_agents
            FROM direction d
            LEFT JOIN service s ON s.direction_id = d.id
            LEFT JOIN poste p ON p.service_id = s.id
            LEFT JOIN affectation a ON a.service_id = s.id AND a.poste_id = p.id
                 AND a.date_debut <= CURRENT_DATE
                 AND (a.date_fin IS NULL OR a.date_fin >= CURRENT_DATE)
            WHERE d.id = ?
            GROUP BY d.id, d.nom, d.description
            """, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String,Object>> services(Long directionId, String query) {
        String pattern = pattern(query);
        return jdbc.queryForList("""
            SELECT s.id, s.code, s.nom, s.description, s.direction_id,
                   d.nom AS direction,
                   COUNT(DISTINCT p.id) AS nombre_postes,
                   COUNT(DISTINCT a.employe_id) AS nombre_agents
            FROM service s
            LEFT JOIN direction d ON d.id = s.direction_id
            LEFT JOIN poste p ON p.service_id = s.id
            LEFT JOIN affectation a ON a.service_id = s.id AND a.poste_id = p.id
                 AND a.date_debut <= CURRENT_DATE
                 AND (a.date_fin IS NULL OR a.date_fin >= CURRENT_DATE)
            WHERE (? IS NULL OR s.direction_id = ?)
              AND (? IS NULL OR s.nom ILIKE ? OR s.code ILIKE ?
                   OR COALESCE(s.description,'') ILIKE ? OR COALESCE(d.nom,'') ILIKE ?)
            GROUP BY s.id, s.code, s.nom, s.description, s.direction_id, d.nom
            ORDER BY s.nom
            """, directionId, directionId, nullable(query),
                pattern, pattern, pattern, pattern);
    }

    public Map<String,Object> service(Long id) {
        List<Map<String,Object>> rows = jdbc.queryForList("""
            SELECT s.id, s.code, s.nom, s.description, s.direction_id,
                   d.nom AS direction,
                   COUNT(DISTINCT p.id) AS nombre_postes,
                   COUNT(DISTINCT a.employe_id) AS nombre_agents
            FROM service s
            LEFT JOIN direction d ON d.id = s.direction_id
            LEFT JOIN poste p ON p.service_id = s.id
            LEFT JOIN affectation a ON a.service_id = s.id AND a.poste_id = p.id
                 AND a.date_debut <= CURRENT_DATE
                 AND (a.date_fin IS NULL OR a.date_fin >= CURRENT_DATE)
            WHERE s.id = ?
            GROUP BY s.id, s.code, s.nom, s.description, s.direction_id, d.nom
            """, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String,Object>> postes(Long directionId, Long serviceId,
                                           String query, boolean includeVacant) {
        String pattern = pattern(query);
        return jdbc.queryForList("""
            SELECT p.id, p.intitule, p.description,
                   s.id AS service_id, s.code AS code_service, s.nom AS service,
                   d.id AS direction_id, d.nom AS direction,
                   COUNT(DISTINCT a.employe_id) AS nombre_titulaires_actuels
            FROM poste p
            JOIN service s ON s.id = p.service_id
            LEFT JOIN direction d ON d.id = s.direction_id
            LEFT JOIN affectation a ON a.poste_id = p.id AND a.service_id = s.id
                 AND a.date_debut <= CURRENT_DATE
                 AND (a.date_fin IS NULL OR a.date_fin >= CURRENT_DATE)
            WHERE (? IS NULL OR s.direction_id = ?)
              AND (? IS NULL OR s.id = ?)
              AND (? IS NULL OR p.intitule ILIKE ? OR COALESCE(p.description,'') ILIKE ?
                   OR s.nom ILIKE ? OR COALESCE(d.nom,'') ILIKE ?)
            GROUP BY p.id, p.intitule, p.description, s.id, s.code, s.nom, d.id, d.nom
            HAVING (? = TRUE OR COUNT(DISTINCT a.employe_id) > 0)
            ORDER BY d.nom, s.nom, p.intitule
            """, directionId, directionId, serviceId, serviceId, nullable(query),
                pattern, pattern, pattern, pattern, includeVacant);
    }

    public Map<String,Object> poste(Long id) {
        List<Map<String,Object>> rows = jdbc.queryForList("""
            SELECT p.id, p.intitule, p.description,
                   s.id AS service_id, s.code AS code_service, s.nom AS service,
                   d.id AS direction_id, d.nom AS direction,
                   COUNT(DISTINCT a.employe_id) AS nombre_titulaires_actuels
            FROM poste p
            JOIN service s ON s.id = p.service_id
            LEFT JOIN direction d ON d.id = s.direction_id
            LEFT JOIN affectation a ON a.poste_id = p.id AND a.service_id = s.id
                 AND a.date_debut <= CURRENT_DATE
                 AND (a.date_fin IS NULL OR a.date_fin >= CURRENT_DATE)
            WHERE p.id = ?
            GROUP BY p.id, p.intitule, p.description, s.id, s.code, s.nom, d.id, d.nom
            """, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String,Object>> titulaires(Long posteId) {
        return jdbc.queryForList("""
            SELECT e.id AS employe_id, e.matricule, e.nom, e.prenom, a.date_debut
            FROM affectation a
            JOIN employe e ON e.id = a.employe_id
            JOIN poste p ON p.id = a.poste_id
            WHERE a.poste_id = ? AND a.service_id = p.service_id
              AND a.date_debut <= CURRENT_DATE
              AND (a.date_fin IS NULL OR a.date_fin >= CURRENT_DATE)
            ORDER BY e.nom, e.prenom, e.matricule
            """, posteId);
    }

    public List<Map<String,Object>> responsables(Long directionId, Long serviceId, String query) {
        String pattern = pattern(query);
        return jdbc.queryForList("""
            SELECT p.id AS poste_id, p.intitule AS poste,
                   s.id AS service_id, s.nom AS service,
                   d.id AS direction_id, d.nom AS direction,
                   e.id AS employe_id, e.matricule, e.nom, e.prenom, a.date_debut
            FROM affectation a
            JOIN employe e ON e.id = a.employe_id
            JOIN poste p ON p.id = a.poste_id
            JOIN service s ON s.id = a.service_id AND s.id = p.service_id
            LEFT JOIN direction d ON d.id = s.direction_id
            WHERE a.date_debut <= CURRENT_DATE
              AND (a.date_fin IS NULL OR a.date_fin >= CURRENT_DATE)
              AND (p.intitule ILIKE '%chef%' OR p.intitule ILIKE '%directeur%'
                   OR p.intitule ILIKE '%directrice%' OR p.intitule ILIKE '%responsable%')
              AND (? IS NULL OR d.id = ?) AND (? IS NULL OR s.id = ?)
              AND (? IS NULL OR p.intitule ILIKE ? OR s.nom ILIKE ?
                   OR COALESCE(d.nom,'') ILIKE ? OR e.nom ILIKE ?
                   OR COALESCE(e.prenom,'') ILIKE ? OR e.matricule ILIKE ?)
            ORDER BY d.nom, s.nom, p.intitule, e.nom, e.prenom
            """, directionId, directionId, serviceId, serviceId, nullable(query),
                pattern, pattern, pattern, pattern, pattern, pattern);
    }

    public List<Map<String,Object>> hierarchy(Long directionId, Long serviceId, boolean includeVacant) {
        return jdbc.queryForList("""
            SELECT d.id AS direction_id, d.nom AS direction, d.description AS description_direction,
                   s.id AS service_id, s.code AS code_service, s.nom AS service,
                   s.description AS description_service,
                   p.id AS poste_id, p.intitule AS poste, p.description AS description_poste,
                   COUNT(DISTINCT a.employe_id) AS nombre_titulaires_actuels
            FROM direction d
            LEFT JOIN service s ON s.direction_id = d.id
            LEFT JOIN poste p ON p.service_id = s.id
            LEFT JOIN affectation a ON a.poste_id = p.id AND a.service_id = s.id
                 AND a.date_debut <= CURRENT_DATE
                 AND (a.date_fin IS NULL OR a.date_fin >= CURRENT_DATE)
            WHERE (? IS NULL OR d.id = ?) AND (? IS NULL OR s.id = ?)
            GROUP BY d.id, d.nom, d.description, s.id, s.code, s.nom, s.description,
                     p.id, p.intitule, p.description
            HAVING (? = TRUE OR p.id IS NULL OR COUNT(DISTINCT a.employe_id) > 0)
            ORDER BY d.nom, s.nom, p.intitule
            """, directionId, directionId, serviceId, serviceId, includeVacant);
    }

    public List<Map<String,Object>> effectifs(Long directionId, Long serviceId) {
        return jdbc.queryForList("""
            SELECT d.id AS direction_id, d.nom AS direction,
                   s.id AS service_id, s.nom AS service,
                   COUNT(DISTINCT a.employe_id) AS nombre_agents
            FROM direction d
            LEFT JOIN service s ON s.direction_id = d.id
            LEFT JOIN poste p ON p.service_id = s.id
            LEFT JOIN affectation a ON a.service_id = s.id AND a.poste_id = p.id
                 AND a.date_debut <= CURRENT_DATE
                 AND (a.date_fin IS NULL OR a.date_fin >= CURRENT_DATE)
            WHERE (? IS NULL OR d.id = ?) AND (? IS NULL OR s.id = ?)
            GROUP BY d.id, d.nom, s.id, s.nom
            ORDER BY d.nom, s.nom
            """, directionId, directionId, serviceId, serviceId);
    }

    public long countDirections() {
        return scalar("SELECT COUNT(*) FROM direction");
    }

    public long countServices(Long directionId) {
        return directionId == null
                ? scalar("SELECT COUNT(*) FROM service")
                : scalar("SELECT COUNT(*) FROM service WHERE direction_id = ?", directionId);
    }

    public long countPostes(Long directionId, Long serviceId) {
        return scalar("""
            SELECT COUNT(*) FROM poste p JOIN service s ON s.id = p.service_id
            WHERE (? IS NULL OR s.direction_id = ?) AND (? IS NULL OR s.id = ?)
            """, directionId, directionId, serviceId, serviceId);
    }

    private long scalar(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private static Object nullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String pattern(String value) {
        return value == null || value.isBlank() ? null : "%" + value.trim() + "%";
    }
}

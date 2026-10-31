package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class OrganisationRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrganisationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listerDirections(String query) {
        String sql = """
                SELECT d.id, d.nom, d.description,
                       COUNT(DISTINCT s.id) AS nombre_services,
                       COUNT(DISTINCT p.id) AS nombre_postes
                FROM direction d
                LEFT JOIN service s ON s.direction_id = d.id
                LEFT JOIN poste p ON p.service_id = s.id
                WHERE (CAST(? AS TEXT) IS NULL OR d.nom ILIKE ? OR COALESCE(d.description, '') ILIKE ?)
                GROUP BY d.id, d.nom, d.description
                ORDER BY d.nom
                """;
        String pattern = motif(query);
        return jdbcTemplate.queryForList(sql, query, pattern, pattern);
    }

    public Map<String, Object> obtenirDirection(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT d.id, d.nom, d.description,
                       COUNT(DISTINCT s.id) AS nombre_services,
                       COUNT(DISTINCT p.id) AS nombre_postes
                FROM direction d
                LEFT JOIN service s ON s.direction_id = d.id
                LEFT JOIN poste p ON p.service_id = s.id
                WHERE d.id = ?
                GROUP BY d.id, d.nom, d.description
                """, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> listerServices(Long directionId, String query) {
        String pattern = motif(query);
        return jdbcTemplate.queryForList("""
                SELECT s.id, s.code, s.nom, s.description,
                       s.direction_id, d.nom AS direction,
                       COUNT(DISTINCT p.id) AS nombre_postes
                FROM service s
                LEFT JOIN direction d ON d.id = s.direction_id
                LEFT JOIN poste p ON p.service_id = s.id
                WHERE (CAST(? AS BIGINT) IS NULL OR s.direction_id = ?)
                  AND (CAST(? AS TEXT) IS NULL OR s.nom ILIKE ? OR s.code ILIKE ?
                       OR COALESCE(s.description, '') ILIKE ?)
                GROUP BY s.id, s.code, s.nom, s.description, s.direction_id, d.nom
                ORDER BY s.nom
                """, directionId, directionId, query, pattern, pattern, pattern);
    }

    public Map<String, Object> obtenirService(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT s.id, s.code, s.nom, s.description,
                       s.direction_id, d.nom AS direction,
                       COUNT(DISTINCT p.id) AS nombre_postes
                FROM service s
                LEFT JOIN direction d ON d.id = s.direction_id
                LEFT JOIN poste p ON p.service_id = s.id
                WHERE s.id = ?
                GROUP BY s.id, s.code, s.nom, s.description, s.direction_id, d.nom
                """, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> listerPostes(Long serviceId, String query) {
        String pattern = motif(query);
        return jdbcTemplate.queryForList("""
                SELECT p.id, p.intitule, p.description, p.service_id,
                       s.nom AS service, s.direction_id, d.nom AS direction,
                       COUNT(DISTINCT CASE WHEN
                           a.date_debut <= CURRENT_DATE
                           AND (a.date_fin IS NULL OR a.date_fin >= CURRENT_DATE)
                           THEN a.employe_id END) AS nombre_titulaires_actuels
                FROM poste p
                JOIN service s ON s.id = p.service_id
                LEFT JOIN direction d ON d.id = s.direction_id
                LEFT JOIN affectation a ON a.poste_id = p.id AND a.service_id = s.id
                WHERE (CAST(? AS BIGINT) IS NULL OR p.service_id = ?)
                  AND (CAST(? AS TEXT) IS NULL OR p.intitule ILIKE ?
                       OR COALESCE(p.description, '') ILIKE ?)
                GROUP BY p.id, p.intitule, p.description, p.service_id,
                         s.nom, s.direction_id, d.nom
                ORDER BY p.intitule
                """, serviceId, serviceId, query, pattern, pattern);
    }

    public Map<String, Object> obtenirPoste(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.id, p.intitule, p.description, p.service_id,
                       s.nom AS service, s.direction_id, d.nom AS direction,
                       COUNT(DISTINCT CASE WHEN
                           a.date_debut <= CURRENT_DATE
                           AND (a.date_fin IS NULL OR a.date_fin >= CURRENT_DATE)
                           THEN a.employe_id END) AS nombre_titulaires_actuels
                FROM poste p
                JOIN service s ON s.id = p.service_id
                LEFT JOIN direction d ON d.id = s.direction_id
                LEFT JOIN affectation a ON a.poste_id = p.id AND a.service_id = s.id
                WHERE p.id = ?
                GROUP BY p.id, p.intitule, p.description, p.service_id,
                         s.nom, s.direction_id, d.nom
                """, id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> obtenirHierarchie(Long directionId) {
        return jdbcTemplate.queryForList("""
                SELECT d.id AS direction_id, d.nom AS direction,
                       s.id AS service_id, s.code AS code_service,
                       s.nom AS service,
                       p.id AS poste_id, p.intitule AS poste,
                       COUNT(DISTINCT CASE WHEN
                           a.date_debut <= CURRENT_DATE
                           AND (a.date_fin IS NULL OR a.date_fin >= CURRENT_DATE)
                           THEN a.employe_id END) AS nombre_titulaires_actuels
                FROM direction d
                LEFT JOIN service s ON s.direction_id = d.id
                LEFT JOIN poste p ON p.service_id = s.id
                LEFT JOIN affectation a ON a.poste_id = p.id AND a.service_id = s.id
                WHERE (CAST(? AS BIGINT) IS NULL OR d.id = ?)
                GROUP BY d.id, d.nom, s.id, s.code, s.nom, p.id, p.intitule
                ORDER BY d.nom, s.nom, p.intitule
                """, directionId, directionId);
    }

    public List<Map<String, Object>> listerResponsables(Long directionId, Long serviceId) {
        return jdbcTemplate.queryForList("""
                SELECT p.id AS poste_id, p.intitule AS fonction,
                       s.id AS service_id, s.nom AS service,
                       d.id AS direction_id, d.nom AS direction,
                       e.id AS employe_id, e.matricule, e.nom, e.prenom,
                       a.date_debut, a.date_fin
                FROM poste p
                JOIN service s ON s.id = p.service_id
                LEFT JOIN direction d ON d.id = s.direction_id
                JOIN affectation a ON a.poste_id = p.id AND a.service_id = s.id
                JOIN employe e ON e.id = a.employe_id
                WHERE (p.intitule ILIKE '%chef%'
                       OR p.intitule ILIKE '%directeur%'
                       OR p.intitule ILIKE '%directrice%'
                       OR p.intitule ILIKE '%responsable%')
                  AND a.date_debut <= CURRENT_DATE
                  AND (a.date_fin IS NULL OR a.date_fin >= CURRENT_DATE)
                  AND (CAST(? AS BIGINT) IS NULL OR d.id = ?)
                  AND (CAST(? AS BIGINT) IS NULL OR s.id = ?)
                ORDER BY d.nom, s.nom, p.intitule, e.nom, e.prenom
                """, directionId, directionId, serviceId, serviceId);
    }

    public long compterDirections() {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM direction", Long.class);
        return value == null ? 0 : value;
    }

    public long compterServices(Long directionId) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM service WHERE (CAST(? AS BIGINT) IS NULL OR direction_id = ?)",
                Long.class, directionId, directionId);
        return value == null ? 0 : value;
    }

    public long compterPostes(Long directionId, Long serviceId) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM poste p
                JOIN service s ON s.id = p.service_id
                WHERE (CAST(? AS BIGINT) IS NULL OR s.direction_id = ?)
                  AND (CAST(? AS BIGINT) IS NULL OR s.id = ?)
                """, Long.class, directionId, directionId, serviceId, serviceId);
        return value == null ? 0 : value;
    }

    public long compterEmployes(Long directionId, Long serviceId) {
        Long value = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT a.employe_id)
                FROM affectation a
                JOIN service s ON s.id = a.service_id
                WHERE a.date_debut <= CURRENT_DATE
                  AND (a.date_fin IS NULL OR a.date_fin >= CURRENT_DATE)
                  AND (CAST(? AS BIGINT) IS NULL OR s.direction_id = ?)
                  AND (CAST(? AS BIGINT) IS NULL OR s.id = ?)
                """, Long.class, directionId, directionId, serviceId, serviceId);
        return value == null ? 0 : value;
    }

    private String motif(String query) {
        return query == null || query.isBlank() ? null : "%" + query.trim() + "%";
    }
}

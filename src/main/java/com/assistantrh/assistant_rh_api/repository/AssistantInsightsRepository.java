package com.assistantrh.assistant_rh_api.repository;

import com.assistantrh.assistant_rh_api.observability.PerformanceTelemetry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Repository
public class AssistantInsightsRepository {

    private final JdbcTemplate jdbcTemplate;
    private final PerformanceTelemetry performanceTelemetry;

    @Autowired
    public AssistantInsightsRepository(
            JdbcTemplate jdbcTemplate,
            PerformanceTelemetry performanceTelemetry
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.performanceTelemetry = performanceTelemetry;
    }

    public AssistantInsightsRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new PerformanceTelemetry());
    }

    public Map<String, Object> organizationOverview() {
        return measured("assistant_insights_organization", () ->
                jdbcTemplate.queryForMap("""
                SELECT
                    (SELECT COUNT(*) FROM employe) AS agents,
                    (SELECT COUNT(*) FROM poste) AS postes,
                    (SELECT COUNT(*) FROM service) AS services,
                    (SELECT COUNT(*) FROM direction) AS directions,
                    (SELECT COUNT(*) FROM categorie) AS categories,
                    (SELECT COUNT(*) FROM type_emploi) AS types_emploi,
                    (SELECT COUNT(*) FROM grade) AS grades,
                    (SELECT COUNT(*) FROM classe) AS classes,
                    (SELECT COUNT(*) FROM echelon) AS echelons,
                    (SELECT COUNT(*) FROM niveau_diplome) AS niveaux_diplome
                """)
        );
    }

    public List<Map<String, Object>> employeesByPosition(String query) {
        return measured("assistant_insights_employees_by_position", () ->
                jdbcTemplate.queryForList("""
                SELECT p.id AS poste_id,
                       p.intitule AS poste,
                       COUNT(DISTINCT affectation_actuelle.employe_id) AS agents
                FROM poste p
                LEFT JOIN (
                    SELECT DISTINCT ON (af.employe_id)
                           af.employe_id,
                           af.poste_id
                    FROM affectation af
                    WHERE af.date_fin IS NULL
                      AND af.date_debut <= CURRENT_DATE
                    ORDER BY af.employe_id, af.date_debut DESC, af.id DESC
                ) affectation_actuelle
                  ON affectation_actuelle.poste_id = p.id
                WHERE ? = ''
                   OR LOWER(p.intitule) LIKE '%' || LOWER(?) || '%'
                GROUP BY p.id, p.intitule
                ORDER BY agents DESC, p.intitule
                LIMIT 50
                """, query, query)
        );
    }

    public List<Map<String, Object>> highestDegreeEmployees() {
        return measured("assistant_insights_highest_degree", () ->
                jdbcTemplate.queryForList("""
                WITH niveau_maximal AS (
                    SELECT MAX(n.ordre_academique) AS ordre_academique
                    FROM employe_diplome ed
                    JOIN niveau_diplome n ON n.id = ed.niveau_id
                    WHERE n.ordre_academique IS NOT NULL
                ),
                diplomes_maximaux AS (
                    SELECT ed.employe_id,
                           n.libelle AS niveau_diplome,
                           n.ordre_academique,
                           ed.date_obtention,
                           ROW_NUMBER() OVER (
                               PARTITION BY ed.employe_id
                               ORDER BY ed.date_obtention DESC NULLS LAST, ed.id DESC
                           ) AS rang_agent
                    FROM employe_diplome ed
                    JOIN niveau_diplome n ON n.id = ed.niveau_id
                    JOIN niveau_maximal nm
                      ON nm.ordre_academique = n.ordre_academique
                )
                SELECT e.id AS employe_id,
                       e.matricule,
                       e.prenom,
                       e.nom,
                       dm.niveau_diplome,
                       dm.ordre_academique,
                       dm.date_obtention
                FROM diplomes_maximaux dm
                JOIN employe e ON e.id = dm.employe_id
                WHERE dm.rang_agent = 1
                ORDER BY e.nom, e.prenom, e.id
                LIMIT 50
                """)
        );
    }

    public List<Map<String, Object>> recentAssignments(int limit) {
        return measured("assistant_insights_recent_assignments", () ->
                jdbcTemplate.queryForList("""
                SELECT af.id AS affectation_id,
                       e.id AS employe_id,
                       e.matricule,
                       e.prenom,
                       e.nom,
                       p.intitule AS poste,
                       s.nom AS service,
                       d.nom AS direction,
                       af.date_debut,
                       af.date_fin
                FROM affectation af
                JOIN employe e ON e.id = af.employe_id
                LEFT JOIN poste p ON p.id = af.poste_id
                LEFT JOIN service s ON s.id = af.service_id
                LEFT JOIN direction d ON d.id = s.direction_id
                WHERE af.date_debut <= CURRENT_DATE
                ORDER BY af.date_debut DESC, af.id DESC
                LIMIT ?
                """, limit)
        );
    }

    private <T> T measured(String operation, Supplier<T> query) {
        long startedAt = System.nanoTime();
        boolean succeeded = false;
        try {
            T result = query.get();
            succeeded = true;
            return result;
        } finally {
            performanceTelemetry.databaseQuery(
                    operation,
                    startedAt,
                    succeeded
            );
        }
    }
}

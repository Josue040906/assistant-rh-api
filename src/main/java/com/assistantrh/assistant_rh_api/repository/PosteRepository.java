        package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class PosteRepository {

    private final JdbcTemplate jdbcTemplate;

    public PosteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Récupère tous les postes avec leur service et leur direction.
     */
    public List<Map<String, Object>> findAll() {

        String sql = """
            SELECT
                p.id,
                p.intitule,
                p.description,
                s.id AS service_id,
                s.code AS code_service,
                s.nom AS service,
                d.id AS direction_id,
                d.nom AS direction
            FROM poste p
            JOIN service s
                ON p.service_id = s.id
            LEFT JOIN direction d
                ON s.direction_id = d.id
            ORDER BY p.intitule
            """;

        return jdbcTemplate.queryForList(sql);
    }

    /**
     * Récupère un poste par son identifiant.
     */
    public Map<String, Object> findById(Long id) {

        String sql = """
            SELECT
                p.id,
                p.intitule,
                p.description,
                s.id AS service_id,
                s.code AS code_service,
                s.nom AS service,
                d.id AS direction_id,
                d.nom AS direction
            FROM poste p
            JOIN service s
                ON p.service_id = s.id
            LEFT JOIN direction d
                ON s.direction_id = d.id
            WHERE p.id = ?
            """;

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(sql, id);

        if (results.isEmpty()) {
            return null;
        }

        return results.get(0);
    }

    /**
     * Recherche un poste par son intitulé,
     * sa description, son service ou sa direction.
     */
    public List<Map<String, Object>> search(String query) {

        String recherche = query.trim();

        String sql = """
            SELECT
                p.id,
                p.intitule,
                p.description,
                s.id AS service_id,
                s.code AS code_service,
                s.nom AS service,
                d.id AS direction_id,
                d.nom AS direction
            FROM poste p
            JOIN service s
                ON p.service_id = s.id
            LEFT JOIN direction d
                ON s.direction_id = d.id
            WHERE
                p.intitule ILIKE ?
                OR p.description ILIKE ?
                OR s.code ILIKE ?
                OR s.nom ILIKE ?
                OR d.nom ILIKE ?
            ORDER BY
                CASE
                    WHEN LOWER(p.intitule) = LOWER(?) THEN 1
                    ELSE 2
                END,
                p.intitule,
                s.nom
            """;

        String pattern = "%" + recherche + "%";

        return jdbcTemplate.queryForList(
                sql,
                pattern,
                pattern,
                pattern,
                pattern,
                pattern,
                recherche
        );
    }

    /**
     * Récupère les postes appartenant à un service précis.
     */
    public List<Map<String, Object>> findByService(Long serviceId) {

        String sql = """
        SELECT
            p.id,
            p.intitule,
            p.description,
            s.id AS service_id,
            s.code AS code_service,
            s.nom AS service,
            d.id AS direction_id,
            d.nom AS direction
        FROM poste p
        JOIN service s
            ON p.service_id = s.id
        LEFT JOIN direction d
            ON s.direction_id = d.id
        WHERE
            p.service_id = ?
        ORDER BY
            p.intitule
        """;

        return jdbcTemplate.queryForList(
                sql,
                serviceId
        );
    }


    /**
     * Crée un nouveau poste.
     */
    public Map<String, Object> create(
            String intitule,
            String description,
            Long serviceId
    ) {
        String sql = """
            INSERT INTO poste (
                intitule,
                description,
                service_id
            )
            VALUES (?, ?, ?)
            RETURNING
                id,
                intitule,
                description,
                service_id
            """;

        return jdbcTemplate.queryForMap(
                sql,
                intitule,
                description,
                serviceId
        );
    }

    /**
     * Modifie un poste existant.
     */
    public Map<String, Object> update(
            Long id,
            String intitule,
            String description,
            Long serviceId
    ) {
        String sql = """
            UPDATE poste
            SET
                intitule = ?,
                description = ?,
                service_id = ?
            WHERE id = ?
            RETURNING
                id,
                intitule,
                description,
                service_id
            """;

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(
                        sql,
                        intitule,
                        description,
                        serviceId,
                        id
                );

        if (results.isEmpty()) {
            return null;
        }

        return results.get(0);
    }

    /**
     * Supprime un poste.
     */
    public boolean delete(Long id) {

        String sql = """
            DELETE FROM poste
            WHERE id = ?
            """;

        return jdbcTemplate.update(sql, id) > 0;
    }
}

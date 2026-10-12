package com.assistantrh.assistant_rh_api.repository;

import com.assistantrh.assistant_rh_api.model.Localite;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class LocaliteRepository {

    private static final String COLONNES = """
            SELECT id, niveau, nom, parent_id, source_code, origine
            FROM localite
            """;

    private final JdbcTemplate jdbcTemplate;

    public LocaliteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Localite> findRegions() {
        return jdbcTemplate.query(
                COLONNES + " WHERE niveau = 'REGION' ORDER BY nom, id",
                mapper()
        );
    }

    public List<Localite> findChildren(long parentId, String niveau) {
        return jdbcTemplate.query(
                COLONNES + " WHERE parent_id = ? AND niveau = ? ORDER BY nom, id",
                mapper(),
                parentId,
                niveau
        );
    }

    public Optional<Localite> findById(long id) {
        List<Localite> result = jdbcTemplate.query(
                COLONNES + " WHERE id = ?",
                mapper(),
                id
        );
        return result.stream().findFirst();
    }

    public List<Localite> findPath(long id) {
        return jdbcTemplate.query("""
                WITH RECURSIVE chemin AS (
                    SELECT id, niveau, nom, parent_id, source_code, origine, 0 AS profondeur
                    FROM localite
                    WHERE id = ?
                    UNION ALL
                    SELECT parent.id, parent.niveau, parent.nom, parent.parent_id,
                           parent.source_code, parent.origine, enfant.profondeur + 1
                    FROM localite parent
                    JOIN chemin enfant ON enfant.parent_id = parent.id
                )
                SELECT id, niveau, nom, parent_id, source_code, origine
                FROM chemin
                ORDER BY profondeur DESC
                """,
                mapper(),
                id
        );
    }

    public List<Localite> search(String query, int limit, int offset) {
        return jdbcTemplate.query("""
                SELECT id, niveau, nom, parent_id, source_code, origine
                FROM localite
                WHERE lower(nom) LIKE lower(?) ESCAPE '\\'
                ORDER BY
                    CASE WHEN lower(nom) = lower(?) THEN 0 ELSE 1 END,
                    nom, niveau, id
                LIMIT ? OFFSET ?
                """,
                mapper(),
                "%" + echapperLike(query) + "%",
                query,
                limit,
                offset
        );
    }

    public long countSearch(String query) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM localite
                WHERE lower(nom) LIKE lower(?) ESCAPE '\\'
                """,
                Long.class,
                "%" + echapperLike(query) + "%"
        );
        return count == null ? 0 : count;
    }

    public boolean existsSelectableById(long id) {
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM localite
                    WHERE id = ? AND niveau IN ('COMMUNE', 'FOKONTANY')
                )
                """,
                Boolean.class,
                id
        );
        return Boolean.TRUE.equals(exists);
    }

    private RowMapper<Localite> mapper() {
        return (resultSet, rowNumber) -> {
            Object parentValue = resultSet.getObject("parent_id");
            Long parentId = parentValue instanceof Number number
                    ? number.longValue()
                    : null;
            return new Localite(
                    resultSet.getLong("id"),
                    resultSet.getString("niveau"),
                    resultSet.getString("nom"),
                    parentId,
                    resultSet.getString("source_code"),
                    resultSet.getString("origine")
            );
        };
    }

    private String echapperLike(String valeur) {
        return valeur.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}

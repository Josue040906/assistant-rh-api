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
                p.nombre_places,
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
                p.nombre_places,
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
                p.nombre_places,
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
     * Crée un nouveau poste.
     */
    public Map<String, Object> create(
            String intitule,
            String description,
            Long serviceId,
            int nombrePlaces
    ) {
        String sql = """
            INSERT INTO poste (
                intitule,
                description,
                service_id,
                nombre_places
            )
            VALUES (?, ?, ?, ?)
            RETURNING
                id,
                intitule,
                description,
                service_id,
                nombre_places
            """;

        return jdbcTemplate.queryForMap(
                sql,
                intitule,
                description,
                serviceId,
                nombrePlaces
        );
    }

    /**
     * Modifie un poste existant.
     */
    public Map<String, Object> update(
            Long id,
            String intitule,
            String description,
            Long serviceId,
            int nombrePlaces
    ) {
        String sql = """
            UPDATE poste
            SET
                intitule = ?,
                description = ?,
                service_id = ?,
                nombre_places = ?
            WHERE id = ?
            RETURNING
                id,
                intitule,
                description,
                service_id,
                nombre_places
            """;

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(
                        sql,
                        intitule,
                        description,
                        serviceId,
                        nombrePlaces,
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

    /** Les postes d'un service, d'une direction, ou tous (filtres facultatifs), avec leurs places. */
    public List<Map<String, Object>> postesAvecPlaces(Long serviceId, Long directionId) {
        return jdbcTemplate.queryForList("""
            SELECT p.id, p.intitule, p.description, p.nombre_places,
                   s.id AS service_id, s.code AS code_service, s.nom AS service,
                   d.id AS direction_id, d.nom AS direction
            FROM poste p
            JOIN service s ON s.id = p.service_id
            LEFT JOIN direction d ON d.id = s.direction_id
            WHERE (?::bigint IS NULL OR p.service_id = ?)
              AND (?::bigint IS NULL OR s.direction_id = ?)
            ORDER BY d.nom NULLS LAST, s.nom, p.intitule, p.id
            """, serviceId, serviceId, directionId, directionId);
    }

    /**
     * Les agents en poste (affectation en cours, pas à la retraite) avec ce qui
     * annonce leur départ : une demande de retraite créée, ou le jour où la durée
     * de service exigée (donnée du type RETRAITE) est atteinte.
     */
    public List<Map<String, Object>> occupants(Long serviceId, Long directionId) {
        return jdbcTemplate.queryForList("""
            SELECT a.poste_id, e.id, e.nom, e.prenom, e.matricule, e.photo,
                   te.nom AS type_emploi, ca.code AS categorie, e.categorie_id,
                   (SELECT MIN(d.date_effet) FROM document d
                    JOIN type_document t ON t.id = d.type_document_id
                    WHERE t.code = 'RETRAITE' AND d.statut = 'CREE' AND d.employe_id = e.id) AS depart_demande,
                   (SELECT (e.date_embauche + MAKE_INTERVAL(years => t.anciennete_service_min_annees))::date
                    FROM type_document t WHERE t.code = 'RETRAITE') AS retraite_possible,
                   (SELECT MAX(cg.date_fin) FROM conge cg WHERE cg.employe_id = e.id
                      AND CURRENT_DATE BETWEEN cg.date_debut AND cg.date_fin) AS en_conge_jusquau
            FROM affectation a
            JOIN employe e ON e.id = a.employe_id
            JOIN poste p ON p.id = a.poste_id
            JOIN service s ON s.id = p.service_id
            LEFT JOIN type_emploi te ON te.id = e.type_emploi_id
            LEFT JOIN categorie ca ON ca.id = e.categorie_id
            WHERE a.date_fin IS NULL AND e.date_retraite IS NULL
              AND (?::bigint IS NULL OR p.service_id = ?)
              AND (?::bigint IS NULL OR s.direction_id = ?)
            ORDER BY e.nom, e.prenom
            """, serviceId, serviceId, directionId, directionId);
    }

    /**
     * Qui pourrait reprendre ce poste : les agents de la même direction, hors de
     * ce poste, en activité et sans départ dans l'année, d'une catégorie au moins
     * égale à celle des occupants actuels ; ceux du même service d'abord.
     */
    public List<Map<String, Object>> candidatsReleve(Long posteId) {
        return jdbcTemplate.queryForList("""
            WITH cible AS (
                SELECT p.id, p.service_id, s.direction_id,
                       (SELECT MIN(e.categorie_id) FROM affectation a JOIN employe e ON e.id = a.employe_id
                        WHERE a.poste_id = p.id AND a.date_fin IS NULL AND e.date_retraite IS NULL) AS categorie_min
                FROM poste p JOIN service s ON s.id = p.service_id
                WHERE p.id = ?
            )
            SELECT e.id, e.nom, e.prenom, e.matricule, e.photo, te.nom AS type_emploi,
                   ca.code AS categorie, po.intitule AS poste, s.nom AS service,
                   (s.id = cible.service_id) AS meme_service,
                   ec.code AS grade_code,
                   (SELECT gi.indice FROM grille_indiciaire gi
                    WHERE gi.categorie_id = e.categorie_id AND gi.echelon_id = hc.echelon_id) AS indice
            FROM cible
            JOIN service s ON s.direction_id IS NOT DISTINCT FROM cible.direction_id
            JOIN affectation a ON a.service_id = s.id AND a.date_fin IS NULL
            JOIN employe e ON e.id = a.employe_id
            JOIN poste po ON po.id = a.poste_id
            LEFT JOIN type_emploi te ON te.id = e.type_emploi_id
            LEFT JOIN categorie ca ON ca.id = e.categorie_id
            LEFT JOIN historique_carriere hc ON hc.employe_id = e.id AND hc.date_fin IS NULL
            LEFT JOIN echelon ec ON ec.id = hc.echelon_id
            WHERE a.poste_id <> cible.id
              AND e.date_retraite IS NULL
              AND (cible.categorie_min IS NULL OR e.categorie_id >= cible.categorie_min)
              AND NOT EXISTS (
                  SELECT 1 FROM type_document t
                  WHERE t.code = 'RETRAITE'
                    AND (e.date_embauche + MAKE_INTERVAL(years => t.anciennete_service_min_annees))::date
                        <= CURRENT_DATE + INTERVAL '12 months')
            ORDER BY (s.id = cible.service_id) DESC, e.categorie_id DESC, indice DESC NULLS LAST, e.nom
            LIMIT 6
            """, posteId);
    }
}

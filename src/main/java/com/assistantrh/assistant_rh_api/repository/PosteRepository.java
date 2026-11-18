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
                    WHERE t.code = 'RETRAITE' AND d.statut IN ('CREE', 'ACCEPTE') AND d.employe_id = e.id) AS depart_demande,
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
     * Les agents en activité qui pourraient occuper ce poste (tous, sauf ses titulaires),
     * avec ce qui sert à les conseiller : même service ou direction, ancien passage sur
     * ce poste, catégorie, retraite, autre poste pour lequel ils sont déjà choisis, et
     * si leur départ laisserait leur propre poste sans titulaire.
     * employeId : un seul agent (analyse d'un choix libre), ou null pour tous.
     */
    public List<Map<String, Object>> agentsPourPoste(Long posteId, Integer employeId) {
        return jdbcTemplate.queryForList("""
            WITH cible AS (
                SELECT p.id, p.service_id, s.direction_id,
                       (SELECT MIN(e.categorie_id) FROM affectation a JOIN employe e ON e.id = a.employe_id
                        WHERE a.poste_id = p.id AND a.date_fin IS NULL AND e.date_retraite IS NULL) AS categorie_min
                FROM poste p JOIN service s ON s.id = p.service_id
                WHERE p.id = ?
            )
            SELECT e.id, e.nom, e.prenom, e.matricule, e.photo, te.nom AS type_emploi,
                   ca.code AS categorie, e.categorie_id, po.id AS poste_actuel_id, po.intitule AS poste,
                   s.nom AS service, d.nom AS direction,
                   COALESCE(s.id = cible.service_id, FALSE) AS meme_service,
                   COALESCE(s.direction_id = cible.direction_id, FALSE) AS meme_direction,
                   (SELECT c2.code FROM categorie c2 WHERE c2.id = cible.categorie_min) AS categorie_requise,
                   cible.categorie_min,
                   (SELECT MAX(ad.date_fin) FROM affectation ad
                    WHERE ad.employe_id = e.id AND ad.poste_id = cible.id AND ad.date_fin IS NOT NULL) AS deja_occupe_jusquau,
                   (SELECT MIN(ad.date_debut) FROM affectation ad
                    WHERE ad.employe_id = e.id AND ad.poste_id = cible.id AND ad.date_fin IS NOT NULL) AS deja_occupe_depuis,
                   (SELECT p2.intitule FROM plan_releve pr JOIN poste p2 ON p2.id = pr.poste_id
                    WHERE pr.successeur_id = e.id AND pr.poste_id <> cible.id LIMIT 1) AS deja_choisi_pour,
                   (SELECT COUNT(*) FROM affectation ao JOIN employe eo ON eo.id = ao.employe_id
                    WHERE ao.poste_id = po.id AND ao.date_fin IS NULL AND eo.date_retraite IS NULL) AS occupants_poste_actuel,
                   ec.code AS grade_code, e.date_embauche,
                   (SELECT (e.date_embauche + MAKE_INTERVAL(years => t.anciennete_service_min_annees))::date
                    FROM type_document t WHERE t.code = 'RETRAITE') AS retraite_possible,
                   (SELECT gi.indice FROM grille_indiciaire gi
                    WHERE gi.categorie_id = e.categorie_id AND gi.echelon_id = hc.echelon_id) AS indice
            FROM cible
            CROSS JOIN employe e
            LEFT JOIN affectation a ON a.employe_id = e.id AND a.date_fin IS NULL
            LEFT JOIN poste po ON po.id = a.poste_id
            LEFT JOIN service s ON s.id = a.service_id
            LEFT JOIN direction d ON d.id = s.direction_id
            LEFT JOIN type_emploi te ON te.id = e.type_emploi_id
            LEFT JOIN categorie ca ON ca.id = e.categorie_id
            LEFT JOIN historique_carriere hc ON hc.employe_id = e.id AND hc.date_fin IS NULL
            LEFT JOIN echelon ec ON ec.id = hc.echelon_id
            WHERE e.date_retraite IS NULL
              AND a.poste_id IS DISTINCT FROM cible.id
              AND (?::int IS NULL OR e.id = ?)
            ORDER BY e.id
            """, posteId, employeId, employeId);
    }

    /** Les titulaires actuels du poste et le jour où chacun pourra partir. */
    public List<Map<String, Object>> titulairesDuPoste(Long posteId) {
        return jdbcTemplate.queryForList("""
            SELECT e.id,
                   (SELECT (e.date_embauche + MAKE_INTERVAL(years => t.anciennete_service_min_annees))::date
                    FROM type_document t WHERE t.code = 'RETRAITE') AS retraite_possible
            FROM affectation a JOIN employe e ON e.id = a.employe_id
            WHERE a.poste_id = ? AND a.date_fin IS NULL AND e.date_retraite IS NULL
            """, posteId);
    }

    /** Les compétences de ces agents (employe_id, libelle). */
    public List<Map<String, Object>> competences(List<Integer> employeIds) {
        if (employeIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.queryForList("SELECT employe_id, libelle FROM employe_competence WHERE employe_id IN ("
                + String.join(",", java.util.Collections.nCopies(employeIds.size(), "?")) + ")", employeIds.toArray());
    }

    /** Les diplômes de ces agents : domaine et niveau (employe_id, domaine, niveau, ordre). */
    public List<Map<String, Object>> diplomes(List<Integer> employeIds) {
        if (employeIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
            SELECT ed.employe_id, dom.libelle AS domaine, n.libelle AS niveau, n.ordre_academique AS ordre
            FROM employe_diplome ed
            JOIN niveau_diplome n ON n.id = ed.niveau_id
            LEFT JOIN domaine_etude dom ON dom.id = ed.domaine_id
            WHERE ed.employe_id IN (""" + String.join(",", java.util.Collections.nCopies(employeIds.size(), "?")) + ")",
                employeIds.toArray());
    }

    /** Les formations continues de ces agents (employe_id, intitule, annee). */
    public List<Map<String, Object>> formations(List<Integer> employeIds) {
        if (employeIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.queryForList("SELECT employe_id, intitule, annee FROM employe_formation WHERE employe_id IN ("
                + String.join(",", java.util.Collections.nCopies(employeIds.size(), "?")) + ") ORDER BY annee DESC NULLS LAST",
                employeIds.toArray());
    }
}

package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class EmployeRepository {

    private final JdbcTemplate jdbcTemplate;

    public EmployeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Liste tous les agents avec leur affectation actuelle.
     */
    public List<Map<String, Object>> findAll() {

        String sql = """
            SELECT
                e.id,
                e.matricule,
                e.nom,
                e.prenom,
                e.date_naissance,
                e.date_embauche,
                e.photo,

                a.poste_id,
                p.intitule AS poste,

                a.service_id,
                s.code AS code_service,
                s.nom AS service,

                d.id AS direction_id,
                d.nom AS direction

            FROM employe e

            LEFT JOIN LATERAL (
                SELECT
                    af.poste_id,
                    af.service_id,
                    af.date_debut,
                    af.id
                FROM affectation af
                WHERE af.employe_id = e.id
                  AND af.date_fin IS NULL
                ORDER BY af.date_debut DESC, af.id DESC
                LIMIT 1
            ) a ON TRUE

            LEFT JOIN poste p
                ON p.id = a.poste_id

            LEFT JOIN service s
                ON s.id = a.service_id

            LEFT JOIN direction d
                ON d.id = s.direction_id

            ORDER BY e.id
            """;

        return jdbcTemplate.queryForList(sql);
    }

    /**
     * Récupère un agent par son identifiant.
     */
    public Optional<Map<String, Object>> findById(Integer id) {

        String sql = """
            SELECT
                e.id,
                e.user_id,
                e.matricule,
                e.nom,
                e.prenom,
                e.sexe,
                e.adresse,
                e.cin,
                e.telephone,
                e.date_naissance,
                e.lieu_naissance,
                e.date_embauche,

                e.type_emploi_id,
                te.nom AS type_emploi,

                e.categorie_id,
                c.code AS categorie,

                co.id AS corps_id,
                co.code AS corps,
                co.libelle AS corps_libelle,

                e.lieu_travail,
                e.photo,

                u.email,

                a.id AS affectation_id,
                a.poste_id,
                p.intitule AS poste,
                p.description AS description_poste,

                a.service_id,
                s.code AS code_service,
                s.nom AS service,
                s.description AS description_service,

                d.id AS direction_id,
                d.nom AS direction,

                a.date_debut AS affectation_date_debut,
                a.date_fin AS affectation_date_fin,
                a.reference_acte AS affectation_reference_acte,
                a.observation AS affectation_observation,

                sc.id AS historique_carriere_id,
                ec.classe_id,
                cl.libelle AS classe,
                cl.libelle AS classe_libelle,
                cl.ordre AS classe_ordre,
                sc.echelon_id,
                ec.ordre AS echelon,
                ec.ordre AS echelon_ordre,
                ec.duree_min AS echelon_duree_min,
                sc.date_debut AS carriere_date_debut,
                sc.date_fin AS carriere_date_fin

            FROM employe e

            LEFT JOIN utilisateur u
                ON u.id = e.user_id

            LEFT JOIN type_emploi te
                ON te.id = e.type_emploi_id

            LEFT JOIN categorie c
                ON c.id = e.categorie_id

            LEFT JOIN corps co
                ON co.id = c.corps_id

            LEFT JOIN LATERAL (
                SELECT
                    af.id,
                    af.poste_id,
                    af.service_id,
                    af.date_debut,
                    af.date_fin,
                    af.reference_acte,
                    af.observation
                FROM affectation af
                WHERE af.employe_id = e.id
                  AND af.date_fin IS NULL
                ORDER BY af.date_debut DESC, af.id DESC
                LIMIT 1
            ) a ON TRUE

            LEFT JOIN poste p
                ON p.id = a.poste_id

            LEFT JOIN service s
                ON s.id = a.service_id

            LEFT JOIN direction d
                ON d.id = s.direction_id

            LEFT JOIN LATERAL (
                SELECT
                    sc2.id,
                    sc2.echelon_id,
                    sc2.date_debut,
                    sc2.date_fin
                FROM historique_carriere sc2
                WHERE sc2.employe_id = e.id
                  AND sc2.date_fin IS NULL
                ORDER BY sc2.date_debut DESC, sc2.id DESC
                LIMIT 1
            ) sc ON TRUE

            LEFT JOIN echelon ec
                ON ec.id = sc.echelon_id

            LEFT JOIN classe cl
                ON cl.id = ec.classe_id

            WHERE e.id = ?
            """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(sql, id);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }

    /**
     * Vérifie si un CIN est déjà utilisé par un autre agent.
     */
    public boolean existsByCinAndIdNot(String cin, Integer employeId) {

        String sql = """
        SELECT COUNT(*)
        FROM employe
        WHERE cin = ?
          AND id <> ?
        """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                cin,
                employeId
        );

        return count != null && count > 0;
    }

    /**
     * Récupère l'agent associé à un utilisateur.
     */
    public Optional<Map<String, Object>> findByUserId(Integer userId) {

        String sql = """
            SELECT
                e.id,
                e.user_id,
                e.matricule,
                e.nom,
                e.prenom,
                e.sexe,
                e.adresse,
                e.cin,
                e.telephone,
                e.date_naissance,
                e.lieu_naissance,
                e.date_embauche,

                e.type_emploi_id,
                te.nom AS type_emploi,

                e.categorie_id,
                c.code AS categorie,

                co.id AS corps_id,
                co.code AS corps,
                co.libelle AS corps_libelle,

                e.lieu_travail,
                e.photo,

                u.email,

                a.id AS affectation_id,
                a.poste_id,
                p.intitule AS poste,
                a.service_id,
                s.code AS code_service,
                s.nom AS service,

                d.id AS direction_id,
                d.nom AS direction,

                a.date_debut AS affectation_date_debut,

                sc.id AS historique_carriere_id,
                ec.classe_id,
                cl.libelle AS classe,
                cl.libelle AS classe_libelle,
                cl.ordre AS classe_ordre,
                sc.echelon_id,
                ec.ordre AS echelon,
                ec.ordre AS echelon_ordre,
                ec.duree_min AS echelon_duree_min,
                sc.date_debut AS carriere_date_debut

            FROM employe e

            LEFT JOIN utilisateur u
                ON u.id = e.user_id

            LEFT JOIN type_emploi te
                ON te.id = e.type_emploi_id

            LEFT JOIN categorie c
                ON c.id = e.categorie_id


            LEFT JOIN corps co
                ON co.id = c.corps_id

            LEFT JOIN LATERAL (
                SELECT
                    af.id,
                    af.poste_id,
                    af.service_id,
                    af.date_debut
                FROM affectation af
                WHERE af.employe_id = e.id
                  AND af.date_fin IS NULL
                ORDER BY af.date_debut DESC, af.id DESC
                LIMIT 1
            ) a ON TRUE

            LEFT JOIN poste p
                ON p.id = a.poste_id

            LEFT JOIN service s
                ON s.id = a.service_id

            LEFT JOIN direction d
                ON d.id = s.direction_id

            LEFT JOIN LATERAL (
                SELECT
                    sc2.id,
                    sc2.echelon_id,
                    sc2.date_debut
                FROM historique_carriere sc2
                WHERE sc2.employe_id = e.id
                  AND sc2.date_fin IS NULL
                ORDER BY sc2.date_debut DESC, sc2.id DESC
                LIMIT 1
            ) sc ON TRUE

            LEFT JOIN echelon ec
                ON ec.id = sc.echelon_id

            LEFT JOIN classe cl
                ON cl.id = ec.classe_id

            WHERE e.user_id = ?
            """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(sql, userId);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }

    /**
     * Recherche générale d'agents.
     */
    public List<Map<String, Object>> search(String query) {

        String recherche = query == null ? "" : query.trim();

        if (recherche.isEmpty()) {
            return findAll();
        }

        String sqlServiceExact = """
            SELECT
                e.id,
                e.matricule,
                e.nom,
                e.prenom,
                e.date_naissance,
                e.date_embauche,
                e.photo,

                a.poste_id,
                p.intitule AS poste,

                a.service_id,
                s.code AS code_service,
                s.nom AS service,

                d.id AS direction_id,
                d.nom AS direction,

                1.0 AS pertinence

            FROM employe e

            LEFT JOIN LATERAL (
                SELECT
                    af.poste_id,
                    af.service_id,
                    af.date_debut,
                    af.id
                FROM affectation af
                WHERE af.employe_id = e.id
                  AND af.date_fin IS NULL
                ORDER BY af.date_debut DESC, af.id DESC
                LIMIT 1
            ) a ON TRUE

            LEFT JOIN poste p
                ON p.id = a.poste_id

            LEFT JOIN service s
                ON s.id = a.service_id

            LEFT JOIN direction d
                ON d.id = s.direction_id

            WHERE LOWER(s.code) = LOWER(?)

            ORDER BY e.id
            """;

        List<Map<String, Object>> serviceExact =
                jdbcTemplate.queryForList(
                        sqlServiceExact,
                        recherche
                );

        if (!serviceExact.isEmpty()) {
            return serviceExact;
        }

        String sql = """
            SELECT
                e.id,
                e.matricule,
                e.nom,
                e.prenom,
                e.date_naissance,
                e.date_embauche,
                e.photo,

                a.poste_id,
                p.intitule AS poste,

                a.service_id,
                s.code AS code_service,
                s.nom AS service,

                d.id AS direction_id,
                d.nom AS direction,

                GREATEST(
                    similarity(lower(e.nom), lower(?)),
                    similarity(lower(e.prenom), lower(?)),
                    similarity(
                        lower(e.prenom || ' ' || e.nom),
                        lower(?)
                    ),
                    similarity(
                        lower(e.nom || ' ' || e.prenom),
                        lower(?)
                    )
                ) AS pertinence

            FROM employe e

            LEFT JOIN LATERAL (
                SELECT
                    af.poste_id,
                    af.service_id,
                    af.date_debut,
                    af.id
                FROM affectation af
                WHERE af.employe_id = e.id
                  AND af.date_fin IS NULL
                ORDER BY af.date_debut DESC, af.id DESC
                LIMIT 1
            ) a ON TRUE

            LEFT JOIN poste p
                ON p.id = a.poste_id

            LEFT JOIN service s
                ON s.id = a.service_id

            LEFT JOIN direction d
                ON d.id = s.direction_id

            WHERE
                e.nom ILIKE ?
                OR e.prenom ILIKE ?
                OR e.matricule ILIKE ?
                OR CONCAT(e.prenom, ' ', e.nom) ILIKE ?
                OR CONCAT(e.nom, ' ', e.prenom) ILIKE ?
                OR p.intitule ILIKE ?
                OR s.code ILIKE ?
                OR s.nom ILIKE ?
                OR d.nom ILIKE ?

                OR similarity(lower(e.nom), lower(?)) >= 0.30
                OR similarity(lower(e.prenom), lower(?)) >= 0.30
                OR similarity(
                    lower(e.prenom || ' ' || e.nom),
                    lower(?)
                ) >= 0.30
                OR similarity(
                    lower(e.nom || ' ' || e.prenom),
                    lower(?)
                ) >= 0.30

            ORDER BY
                pertinence DESC,
                e.id
            """;

        String pattern = "%" + recherche + "%";

        return jdbcTemplate.queryForList(
                sql,
                recherche,
                recherche,
                recherche,
                recherche,

                pattern,
                pattern,
                pattern,
                pattern,
                pattern,
                pattern,
                pattern,
                pattern,
                pattern,

                recherche,
                recherche,
                recherche,
                recherche
        );
    }

    /**
     * Recherche un profil par nom, prénom ou matricule.
     */
    public Optional<Map<String, Object>> findProfile(String query) {

        String recherche = query == null ? "" : query.trim();

        if (recherche.isEmpty()) {
            return Optional.empty();
        }

        String sql = """
            SELECT
                e.id,
                e.matricule,
                e.nom,
                e.prenom,
                e.date_naissance,
                e.date_embauche,
                e.photo,

                a.poste_id,
                p.intitule AS poste,
                p.description AS description_poste,

                a.service_id,
                s.code AS code_service,
                s.nom AS service,
                s.description AS description_service,

                d.id AS direction_id,
                d.nom AS direction

            FROM employe e

            LEFT JOIN LATERAL (
                SELECT
                    af.poste_id,
                    af.service_id,
                    af.date_debut,
                    af.id
                FROM affectation af
                WHERE af.employe_id = e.id
                  AND af.date_fin IS NULL
                ORDER BY af.date_debut DESC, af.id DESC
                LIMIT 1
            ) a ON TRUE

            LEFT JOIN poste p
                ON p.id = a.poste_id

            LEFT JOIN service s
                ON s.id = a.service_id

            LEFT JOIN direction d
                ON d.id = s.direction_id

            WHERE
                e.nom ILIKE ?
                OR e.prenom ILIKE ?
                OR e.matricule ILIKE ?
                OR CONCAT(e.prenom, ' ', e.nom) ILIKE ?
                OR CONCAT(e.nom, ' ', e.prenom) ILIKE ?

            ORDER BY
                CASE
                    WHEN LOWER(CONCAT(e.prenom, ' ', e.nom))
                         = LOWER(?) THEN 1
                    WHEN LOWER(CONCAT(e.nom, ' ', e.prenom))
                         = LOWER(?) THEN 2
                    WHEN LOWER(e.nom) = LOWER(?) THEN 3
                    WHEN LOWER(e.prenom) = LOWER(?) THEN 4
                    ELSE 5
                END,
                e.id

            LIMIT 1
            """;

        String pattern = "%" + recherche + "%";

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList(
                        sql,
                        pattern,
                        pattern,
                        pattern,
                        pattern,
                        pattern,
                        recherche,
                        recherche,
                        recherche,
                        recherche
                );

        if (results.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(results.get(0));
    }

    /**
     * Recherche par matricule.
     * Important pour l'inscription utilisateur.
     */
    public Optional<Map<String, Object>> findByMatricule(
            String matricule
    ) {

        if (matricule == null || matricule.isBlank()) {
            return Optional.empty();
        }

        String sql = """
            SELECT
                e.id,
                e.user_id,
                e.matricule,
                e.nom,
                e.prenom,

                a.service_id

            FROM employe e

            LEFT JOIN LATERAL (
                SELECT
                    af.service_id,
                    af.date_debut,
                    af.id
                FROM affectation af
                WHERE af.employe_id = e.id
                  AND af.date_fin IS NULL
                ORDER BY af.date_debut DESC, af.id DESC
                LIMIT 1
            ) a ON TRUE

            WHERE LOWER(e.matricule) = LOWER(?)
            """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(
                        sql,
                        matricule.trim()
                );

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }

    /**
     * Associe un utilisateur à un agent.
     */
    public boolean associerUtilisateur(
            Integer employeId,
            Integer userId
    ) {

        String sql = """
            UPDATE employe
            SET user_id = ?
            WHERE id = ?
              AND user_id IS NULL
            """;

        int lignesModifiees =
                jdbcTemplate.update(
                        sql,
                        userId,
                        employeId
                );

        return lignesModifiees > 0;
    }

    /**
     * Crée un agent.
     *
     * Le poste et le service ne sont PAS enregistrés ici :
     * ils seront enregistrés dans affectation.
     */
    public Integer create(
            String matricule,
            String nom,
            String prenom,
            String sexe,
            String adresse,
            String cin,
            String telephone,
            java.sql.Date dateNaissance,
            String lieuNaissance,
            java.sql.Date dateEmbauche,
            Integer typeEmploiId,
            Integer categorieId,
            String lieuTravail,
            String photo,
            Integer userId
    ) {

        String sql = """
            INSERT INTO employe (
                matricule,
                nom,
                prenom,
                sexe,
                adresse,
                cin,
                telephone,
                date_naissance,
                lieu_naissance,
                date_embauche,
                type_emploi_id,
                categorie_id,
                lieu_travail,
                photo,
                user_id
            )
            VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?
            )
            RETURNING id
            """;

        return jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                matricule,
                nom,
                prenom,
                sexe,
                adresse,
                cin,
                telephone,
                dateNaissance,
                lieuNaissance,
                dateEmbauche,
                typeEmploiId,
                categorieId,
                lieuTravail,
                photo,
                userId
        );
    }

    /**
     * Modifie uniquement les informations personnelles de l'agent.
     */
    public int update(
            Integer employeId,
            String nom,
            String prenom,
            String sexe,
            String adresse,
            String cin,
            String telephone,
            java.sql.Date dateNaissance,
            String lieuNaissance
    ) {

        String sql = """
            UPDATE employe
            SET
                nom = ?,
                prenom = ?,
                sexe = ?,
                adresse = ?,
                cin = ?,
                telephone = ?,
                date_naissance = ?,
                lieu_naissance = ?
            WHERE id = ?
            """;

        return jdbcTemplate.update(
                sql,
                nom,
                prenom,
                sexe,
                adresse,
                cin,
                telephone,
                dateNaissance,
                lieuNaissance,
                employeId
        );
    }

    /**
     * Met à jour uniquement le lieu de travail actuel.
     *
     * Les IDs du poste/service sont désormais gérés par affectation.
     */
    public int updateLieuTravail(
            Integer id,
            String lieuTravail
    ) {

        String sql = """
            UPDATE employe
            SET lieu_travail = ?
            WHERE id = ?
            """;

        return jdbcTemplate.update(
                sql,
                lieuTravail,
                id
        );
    }

    /**
     * Met à jour la photo.
     */
    public int updatePhoto(
            Integer id,
            String photo
    ) {

        String sql = """
            UPDATE employe
            SET photo = ?
            WHERE id = ?
            """;

        return jdbcTemplate.update(
                sql,
                photo,
                id
        );
    }

    /**
     * Profil détaillé de l'utilisateur connecté.
     */
    public Optional<Map<String, Object>> findProfilByUserId(
            Integer userId
    ) {

        String sql = """
            SELECT
                e.id,
                e.user_id,
                e.matricule,
                e.nom,
                e.prenom,
                e.sexe,
                e.adresse,
                e.cin,
                e.telephone,
                e.date_naissance,
                e.lieu_naissance,
                e.date_embauche,

                e.type_emploi_id,
                te.nom AS type_emploi,

                e.categorie_id,
                c.code AS categorie,


                co.id AS corps_id,
                co.code AS code_corps,
                co.libelle AS corps,

                a.poste_id,
                p.intitule AS poste,

                a.service_id,
                s.code AS code_service,
                s.nom AS service,

                d.id AS direction_id,
                d.nom AS direction,

                a.date_debut AS affectation_date_debut,
                a.reference_acte AS affectation_reference_acte,
                a.observation AS affectation_observation,

                sc.id AS historique_carriere_id,
                ec.classe_id,
                cl.libelle AS classe,
                cl.libelle AS classe_libelle,
                cl.ordre AS classe_ordre,
                sc.echelon_id,
                ec.ordre AS echelon,
                ec.ordre AS echelon_ordre,
                ec.duree_min AS echelon_duree_min,
                sc.date_debut AS carriere_date_debut,

                e.lieu_travail,
                e.photo,
                u.email

            FROM employe e

            LEFT JOIN utilisateur u
                ON u.id = e.user_id

            LEFT JOIN type_emploi te
                ON te.id = e.type_emploi_id

            LEFT JOIN categorie c
                ON c.id = e.categorie_id


            LEFT JOIN corps co
                ON co.id = c.corps_id

            LEFT JOIN LATERAL (
                SELECT
                    af.id,
                    af.poste_id,
                    af.service_id,
                    af.date_debut,
                    af.date_fin,
                    af.reference_acte,
                    af.observation
                FROM affectation af
                WHERE af.employe_id = e.id
                  AND af.date_fin IS NULL
                ORDER BY af.date_debut DESC, af.id DESC
                LIMIT 1
            ) a ON TRUE

            LEFT JOIN poste p
                ON p.id = a.poste_id

            LEFT JOIN service s
                ON s.id = a.service_id

            LEFT JOIN direction d
                ON d.id = s.direction_id

            LEFT JOIN LATERAL (
                SELECT
                    sc2.id,
                    sc2.echelon_id,
                    sc2.date_debut,
                    sc2.date_fin
                FROM historique_carriere sc2
                WHERE sc2.employe_id = e.id
                  AND sc2.date_fin IS NULL
                ORDER BY sc2.date_debut DESC, sc2.id DESC
                LIMIT 1
            ) sc ON TRUE

            LEFT JOIN echelon ec
                ON ec.id = sc.echelon_id

            LEFT JOIN classe cl
                ON cl.id = ec.classe_id

            WHERE e.user_id = ?
            """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(sql, userId);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }
}
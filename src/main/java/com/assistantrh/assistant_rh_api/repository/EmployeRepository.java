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
                p.intitule AS poste,
                s.code AS code_service,
                s.nom AS service,
                d.nom AS direction
            FROM employe e
            JOIN poste p ON e.poste_id = p.id
            JOIN service s ON e.service_id = s.id
            LEFT JOIN direction d ON s.direction_id = d.id
            ORDER BY e.id
            """;

        return jdbcTemplate.queryForList(sql);
    }

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
            e.poste_id,
            p.intitule AS poste,
            p.description AS description_poste,
            e.service_id,
            s.code AS code_service,
            s.nom AS service,
            s.description AS description_service,
            d.id AS direction_id,
            d.nom AS direction,
            e.type_emploi_id,
            e.categorie_id,
            e.grade_id,
            e.lieu_travail,
            e.photo
        FROM employe e
        JOIN poste p ON e.poste_id = p.id
        JOIN service s ON e.service_id = s.id
        LEFT JOIN direction d ON s.direction_id = d.id
        WHERE e.id = ?
        """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(sql, id);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }

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

        sc.id AS situation_carriere_id,
        sc.statut_agent_id,
        sa.code AS statut_agent,
        sc.classe_id,
        cl.code AS classe,
        sc.echelon_id,
        ec.code AS echelon,
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

    LEFT JOIN affectation a
        ON a.employe_id = e.id
        AND a.date_fin IS NULL

    LEFT JOIN poste p
        ON p.id = a.poste_id

    LEFT JOIN service s
        ON s.id = a.service_id

    LEFT JOIN direction d
        ON d.id = s.direction_id

    LEFT JOIN situation_carriere sc
        ON sc.employe_id = e.id
        AND sc.date_fin IS NULL

    LEFT JOIN statut_agent sa
        ON sa.id = sc.statut_agent_id

    LEFT JOIN classe cl
        ON cl.id = sc.classe_id

    LEFT JOIN echelon ec
        ON ec.id = sc.echelon_id

    WHERE e.user_id = ?
    """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(sql, userId);

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }
    public List<Map<String, Object>> search(String query) {

        String recherche = query.trim();

        // 1. Si la requête correspond exactement à un code de service,
        // on recherche uniquement dans ce service.
        String sqlServiceExact = """
        SELECT
            e.id,
            e.matricule,
            e.nom,
            e.prenom,
            e.date_naissance,
            e.date_embauche,
            e.photo,
            p.intitule AS poste,
            s.code AS code_service,
            s.nom AS service,
            d.nom AS direction,

            1.0 AS pertinence

        FROM employe e
        JOIN poste p ON e.poste_id = p.id
        JOIN service s ON e.service_id = s.id
        LEFT JOIN direction d ON s.direction_id = d.id

        WHERE LOWER(s.code) = LOWER(?)

        ORDER BY e.id
        """;

        List<Map<String, Object>> serviceExact =
                jdbcTemplate.queryForList(sqlServiceExact, recherche);

        if (!serviceExact.isEmpty()) {
            return serviceExact;
        }

        // 2. Recherche générale : nom, prénom, matricule,
        // poste, service, direction et fautes de frappe.
        String sql = """
        SELECT
            e.id,
            e.matricule,
            e.nom,
            e.prenom,
            e.date_naissance,
            e.date_embauche,
            e.photo,
            p.intitule AS poste,
            s.code AS code_service,
            s.nom AS service,
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
        JOIN poste p ON e.poste_id = p.id
        JOIN service s ON e.service_id = s.id
        LEFT JOIN direction d ON s.direction_id = d.id

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
                recherche, recherche, recherche, recherche,
                pattern, pattern, pattern, pattern, pattern,
                pattern, pattern, pattern, pattern,
                recherche, recherche, recherche, recherche
        );
    }

    public Optional<Map<String, Object>> findProfile(String query) {

        String recherche = query.trim();

        String sql = """
        SELECT
            e.id,
            e.matricule,
            e.nom,
            e.prenom,
            e.date_naissance,
            e.date_embauche,
            e.photo,
            p.intitule AS poste,
            p.description AS description_poste,
            s.code AS code_service,
            s.nom AS service,
            s.description AS description_service,
            d.nom AS direction
        FROM employe e
        JOIN poste p ON e.poste_id = p.id
        JOIN service s ON e.service_id = s.id
        LEFT JOIN direction d ON s.direction_id = d.id
        WHERE
            e.nom ILIKE ?
            OR e.prenom ILIKE ?
            OR e.matricule ILIKE ?
            OR CONCAT(e.prenom, ' ', e.nom) ILIKE ?
            OR CONCAT(e.nom, ' ', e.prenom) ILIKE ?
        ORDER BY
            CASE
                WHEN LOWER(CONCAT(e.prenom, ' ', e.nom)) =
                     LOWER(?) THEN 1
                WHEN LOWER(CONCAT(e.nom, ' ', e.prenom)) =
                     LOWER(?) THEN 2
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

    public Optional<Map<String, Object>> findByMatricule(String matricule) {

        String sql = """
        SELECT
            id,
            user_id,
            matricule,
            nom,
            prenom,
            service_id
        FROM employe
        WHERE LOWER(matricule) = LOWER(?)
        """;

        List<Map<String, Object>> result =
                jdbcTemplate.queryForList(sql, matricule.trim());

        if (result.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(result.get(0));
    }
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

        int lignesModifiees = jdbcTemplate.update(
                sql,
                userId,
                employeId
        );

        return lignesModifiees > 0;
    }

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
            Integer posteId,
            Integer serviceId,
            Integer typeEmploiId,
            Integer categorieId,
            Integer gradeId,
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
            poste_id,
            service_id,
            type_emploi_id,
            categorie_id,
            grade_id,
            lieu_travail,
            photo,
            user_id
        )
        VALUES (
            ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
            ?, ?, ?, ?, ?, ?, ?, ?
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
                posteId,
                serviceId,
                typeEmploiId,
                categorieId,
                gradeId,
                lieuTravail,
                photo,
                userId
        );
    }

    public int update(
            Integer id,
            String nom,
            String prenom,
            String sexe,
            String adresse,
            String cin,
            String telephone,
            java.sql.Date dateNaissance,
            String lieuNaissance,
            java.sql.Date dateEmbauche,
            String lieuTravail,
            String photo
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
            lieu_naissance = ?,
            date_embauche = ?,
            lieu_travail = ?,
            photo = ?
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
                dateEmbauche,
                lieuTravail,
                photo,
                id
        );
    }
    /**
     * Met à jour l'affectation courante d'un agent.
     */
    public int updateAffectation(
            Integer id,
            Integer posteId,
            Integer serviceId,
            String lieuTravail
    ) {
        String sql = """
            UPDATE employe
            SET
                poste_id = ?,
                service_id = ?,
                lieu_travail = ?
            WHERE id = ?
            """;

        return jdbcTemplate.update(
                sql,
                posteId,
                serviceId,
                lieuTravail,
                id
        );
    }

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

    public int updateProfil(
                Integer userId,
                String adresse,
                String telephone
        ) {

            String sql = """
        UPDATE employe
        SET
            adresse = ?,
            telephone = ?
        WHERE user_id = ?
        """;

            return jdbcTemplate.update(
                    sql,
                    adresse,
                    telephone,
                    userId
            );
    }
    public Optional<Map<String, Object>> findProfilByUserId(Integer userId) {

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

            aff.poste_id,
            p.intitule AS poste,

            aff.service_id,
            s.code AS code_service,
            s.nom AS service,

            d.id AS direction_id,
            d.nom AS direction,

            aff.date_debut AS affectation_date_debut,
            aff.reference_acte AS affectation_reference_acte,
            aff.observation AS affectation_observation,

            aff_lieu.lieu_travail,

            sc.id AS situation_carriere_id,
            sc.statut_agent_id,
            sa.code AS statut_agent,

            sc.classe_id,
            cl.code AS classe,

            sc.echelon_id,
            ec.code AS echelon,

            sc.date_debut AS carriere_date_debut,
            sc.reference_acte AS carriere_reference_acte,
            sc.observation AS carriere_observation,

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
        ) aff
            ON TRUE

        LEFT JOIN poste p
            ON p.id = aff.poste_id

        LEFT JOIN service s
            ON s.id = aff.service_id

        LEFT JOIN direction d
            ON d.id = s.direction_id

        LEFT JOIN LATERAL (
            SELECT
                af.lieu_travail
            FROM affectation af
            WHERE af.employe_id = e.id
              AND af.date_fin IS NULL
            ORDER BY af.date_debut DESC, af.id DESC
            LIMIT 1
        ) aff_lieu
            ON TRUE

        LEFT JOIN LATERAL (
            SELECT *
            FROM situation_carriere sc2
            WHERE sc2.employe_id = e.id
              AND sc2.date_fin IS NULL
            ORDER BY sc2.date_debut DESC, sc2.id DESC
            LIMIT 1
        ) sc
            ON TRUE

        LEFT JOIN statut_agent sa
            ON sa.id = sc.statut_agent_id

        LEFT JOIN classe cl
            ON cl.id = sc.classe_id

        LEFT JOIN echelon ec
            ON ec.id = sc.echelon_id

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
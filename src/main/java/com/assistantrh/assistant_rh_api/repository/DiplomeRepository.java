package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DiplomeRepository {

    private final JdbcTemplate jdbcTemplate;

    public DiplomeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> findNiveauxFormulaire() {
        return jdbcTemplate.queryForList("""
                SELECT n.id, n.code, n.libelle, n.ordre_academique
                FROM niveau_diplome n
                ORDER BY n.ordre_academique NULLS LAST, n.libelle
                """);
    }

    public List<Map<String, Object>> findDomainesFormulaire(Integer niveauId) {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT d.id, d.libelle
                FROM offre_formation o
                JOIN domaine_etude d ON d.id = o.domaine_id
                JOIN etablissement e ON e.id = o.etablissement_id
                WHERE o.actif
                  AND d.actif
                  AND e.actif
                  AND e.statut_validation = 'VALIDE'
                  AND (?::integer IS NULL OR o.niveau_id = ?)
                ORDER BY d.libelle
                """, niveauId, niveauId);
    }

    public List<Map<String, Object>> findEtablissementsFormulaire(
            Integer niveauId,
            Integer domaineId
    ) {
        return jdbcTemplate.queryForList("""
                SELECT DISTINCT e.id, e.nom, e.ville
                FROM offre_formation o
                JOIN etablissement e ON e.id = o.etablissement_id
                JOIN domaine_etude d ON d.id = o.domaine_id
                WHERE o.niveau_id = ?
                  AND o.domaine_id = ?
                  AND o.actif
                  AND d.actif
                  AND e.actif
                  AND e.statut_validation = 'VALIDE'
                ORDER BY e.nom
                """, niveauId, domaineId);
    }

    public List<Map<String, Object>> findMentionsFormulaire() {
        return jdbcTemplate.queryForList("""
                SELECT id, libelle
                FROM mention
                WHERE actif
                ORDER BY id
                """);
    }

    public boolean niveauExiste(Integer niveauId) {
        Boolean found = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM niveau_diplome WHERE id = ?)",
                Boolean.class, niveauId
        );
        return Boolean.TRUE.equals(found);
    }

    public boolean domaineDisponible(Integer niveauId, Integer domaineId) {
        Boolean found = jdbcTemplate.queryForObject("""
                SELECT EXISTS(
                    SELECT 1
                    FROM offre_formation o
                    JOIN domaine_etude d ON d.id = o.domaine_id
                    JOIN etablissement e ON e.id = o.etablissement_id
                    WHERE o.niveau_id = ?
                      AND o.domaine_id = ?
                      AND o.actif
                      AND d.actif
                      AND e.actif
                      AND e.statut_validation = 'VALIDE'
                )
                """, Boolean.class, niveauId, domaineId);
        return Boolean.TRUE.equals(found);
    }

    public Optional<Map<String, Object>> findOffreFormulaire(
            Integer niveauId,
            Integer domaineId,
            Integer etablissementId
    ) {
        List<Map<String, Object>> result = jdbcTemplate.queryForList("""
                SELECT n.libelle AS niveau,
                       d.libelle AS domaine,
                       e.nom AS etablissement
                FROM offre_formation o
                JOIN niveau_diplome n ON n.id = o.niveau_id
                JOIN domaine_etude d ON d.id = o.domaine_id
                JOIN etablissement e ON e.id = o.etablissement_id
                WHERE o.niveau_id = ?
                  AND o.domaine_id = ?
                  AND o.etablissement_id = ?
                  AND o.actif
                  AND d.actif
                  AND e.actif
                  AND e.statut_validation = 'VALIDE'
                """, niveauId, domaineId, etablissementId);
        return result.stream().findFirst();
    }

    public Optional<String> findMentionFormulaire(Integer mentionId) {
        List<String> result = jdbcTemplate.queryForList("""
                SELECT libelle
                FROM mention
                WHERE id = ?
                  AND actif
                """, String.class, mentionId);
        return result.stream().findFirst();
    }

    public int ajouterDemandeEtablissement(String nom, String ville) {
        return jdbcTemplate.update("""
                INSERT INTO etablissement (
                    nom, ville, actif, statut_validation
                )
                VALUES (?, ?, FALSE, 'A_VALIDER')
                ON CONFLICT (lower(btrim(nom))) DO NOTHING
                """, nom, ville);
    }

    public boolean verrouillerEmploye(Integer employeId) {
        ResultSetExtractor<Boolean> extractor = resultSet -> resultSet.next();
        return jdbcTemplate.query("""
                SELECT id FROM employe WHERE id = ? FOR UPDATE
                """, extractor, employeId);
    }

    public List<Map<String, Object>> findByEmployeId(Integer employeId) {
        return jdbcTemplate.queryForList("""
                SELECT ed.id,
                       ed.employe_id,
                       n.libelle AS intitule,
                       dom.libelle AS domaine,
                       ed.niveau_id,
                       n.code AS niveau_code,
                       n.libelle AS niveau,
                       e.nom AS etablissement,
                       ed.etablissement_id,
                       ed.domaine_id,
                       ed.date_obtention,
                       m.libelle AS mention,
                       ed.mention_id,
                       ed.principal,
                       ed.observation
                FROM employe_diplome ed
                JOIN niveau_diplome n ON n.id = ed.niveau_id
                LEFT JOIN domaine_etude dom ON dom.id = ed.domaine_id
                LEFT JOIN etablissement e ON e.id = ed.etablissement_id
                LEFT JOIN mention m ON m.id = ed.mention_id
                WHERE ed.employe_id = ?
                ORDER BY ed.date_obtention DESC NULLS LAST, ed.id DESC
                """, employeId);
    }

    public int ajouter(
            Integer employeId,
            Integer niveauId,
            Integer domaineId,
            Integer etablissementId,
            Date dateObtention,
            Integer mentionId,
            String observation
    ) {
        return jdbcTemplate.update("""
                INSERT INTO employe_diplome (
                    employe_id, etablissement_id, date_obtention,
                    principal, observation, niveau_id, domaine_id, mention_id
                )
                VALUES (?, ?, ?, FALSE, ?, ?, ?, ?)
                """,
                employeId, etablissementId, dateObtention, observation,
                niveauId, domaineId, mentionId
        );
    }

    public List<Map<String, Object>> findDiplomesValidesPourReclassement(
            Integer employeId
    ) {
        return jdbcTemplate.queryForList("""
                SELECT ed.id AS employe_diplome_id,
                       n.libelle AS intitule,
                       ed.date_obtention,
                       ed.principal,
                       ed.niveau_id,
                       n.libelle AS niveau,
                       n.ordre_academique,
                       c.id AS categorie_id,
                       c.code AS categorie_code,
                       co.id AS corps_id,
                       co.code AS corps_code,
                       co.libelle AS corps_libelle
                FROM employe_diplome ed
                JOIN niveau_diplome n ON n.id = ed.niveau_id
                LEFT JOIN categorie c ON c.id = n.categorie_id
                LEFT JOIN corps co ON co.id = c.corps_id
                WHERE ed.employe_id = ?
                ORDER BY ed.date_obtention DESC NULLS LAST, ed.id DESC
                """, employeId);
    }

    public List<Map<String, Object>> findReclassementRules() {
        return jdbcTemplate.queryForList("""
                SELECT n.id AS niveau_id,
                       n.code AS niveau_code,
                       n.libelle AS niveau,
                       n.ordre_academique,
                       c.id AS categorie_id,
                       c.code AS categorie_code,
                       co.id AS corps_id,
                       co.code AS corps_code,
                       co.libelle AS corps_libelle
                FROM niveau_diplome n
                LEFT JOIN categorie c ON c.id = n.categorie_id
                LEFT JOIN corps co ON co.id = c.corps_id
                ORDER BY n.ordre_academique NULLS LAST, n.id
                """);
    }

    public List<Map<String, Object>> findCareerTargets() {
        return jdbcTemplate.queryForList("""
                SELECT ec.id AS echelon_id,
                       ec.classe_id,
                       cl.libelle AS classe,
                       ec.ordre AS echelon,
                       ec.duree_min
                FROM echelon ec
                JOIN classe cl ON cl.id = ec.classe_id
                ORDER BY cl.ordre, ec.ordre
                """);
    }

    public int setOrdreAcademique(Integer niveauId, Integer ordre) {
        return jdbcTemplate.update(
                "UPDATE niveau_diplome SET ordre_academique = ? WHERE id = ?",
                ordre, niveauId
        );
    }

}

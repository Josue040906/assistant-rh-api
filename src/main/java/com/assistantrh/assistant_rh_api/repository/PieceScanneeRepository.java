package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Les pièces scannées et l'existence de leur cible (document ou diplôme). */
@Repository
public class PieceScanneeRepository {
    private final JdbcTemplate jdbcTemplate;

    public PieceScanneeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean cibleExiste(String cibleType, Integer cibleId) {
        String table = switch (cibleType) {
            case "DOCUMENT" -> "document";
            case "DIPLOME" -> "employe_diplome";
            default -> throw new IllegalArgumentException("Type de cible inconnu : " + cibleType);
        };
        Integer nombre = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE id = ?", Integer.class, cibleId);
        return nombre != null && nombre > 0;
    }

    public Integer ajouter(String cibleType, Integer cibleId, String nomFichier,
                           String typeMime, int taille, String chemin, Integer deposePar) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO piece_scannee (
                    cible_type, cible_id, nom_fichier, type_mime, taille, chemin, depose_par
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Integer.class, cibleType, cibleId, nomFichier, typeMime, taille, chemin, deposePar);
    }

    /** Description des pièces d'une cible, sans leur chemin de stockage. */
    public List<Map<String, Object>> lister(String cibleType, Integer cibleId) {
        return jdbcTemplate.queryForList("""
                SELECT id, nom_fichier, type_mime, taille, depose_le
                FROM piece_scannee
                WHERE cible_type = ? AND cible_id = ?
                ORDER BY depose_le, id
                """, cibleType, cibleId);
    }

    public Optional<Map<String, Object>> trouver(Integer id) {
        return jdbcTemplate.queryForList("""
                SELECT id, cible_type, cible_id, nom_fichier, type_mime, chemin
                FROM piece_scannee WHERE id = ?
                """, id).stream().findFirst();
    }
}

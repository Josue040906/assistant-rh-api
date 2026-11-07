package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class IdempotenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public IdempotenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Réserve la clé ; false si elle existe déjà (requête rejouée ou concurrente). */
    public boolean reserver(int utilisateurId, String cle, String route, String empreinte) {
        return jdbcTemplate.update("""
                INSERT INTO requete_idempotente (utilisateur_id, cle, route, empreinte)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (utilisateur_id, cle) DO NOTHING
                """,
                utilisateurId, cle, route, empreinte
        ) == 1;
    }

    public Optional<Enregistrement> trouver(int utilisateurId, String cle) {
        return jdbcTemplate.query("""
                SELECT route, empreinte, statut_http, reponse
                FROM requete_idempotente
                WHERE utilisateur_id = ? AND cle = ?
                """,
                (resultSet, rowNumber) -> new Enregistrement(
                        resultSet.getString("route"),
                        resultSet.getString("empreinte"),
                        (Integer) resultSet.getObject("statut_http"),
                        resultSet.getString("reponse")
                ),
                utilisateurId, cle
        ).stream().findFirst();
    }

    public void terminer(int utilisateurId, String cle, int statutHttp, String reponse) {
        jdbcTemplate.update("""
                UPDATE requete_idempotente
                SET statut_http = ?, reponse = ?
                WHERE utilisateur_id = ? AND cle = ?
                """,
                statutHttp, reponse, utilisateurId, cle
        );
    }

    /** Libère une clé réservée dont la requête a échoué : elle pourra resservir. */
    public void liberer(int utilisateurId, String cle) {
        jdbcTemplate.update("""
                DELETE FROM requete_idempotente
                WHERE utilisateur_id = ? AND cle = ? AND statut_http IS NULL
                """,
                utilisateurId, cle
        );
    }

    public record Enregistrement(String route, String empreinte, Integer statutHttp, String reponse) {
    }
}

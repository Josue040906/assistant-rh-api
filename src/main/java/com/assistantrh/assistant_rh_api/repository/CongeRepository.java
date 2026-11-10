package com.assistantrh.assistant_rh_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class CongeRepository {
    /**
     * Jours de congé acquis à un jour donné : le rythme mensuel (donnée du type
     * CONGE) × les mois complets écoulés depuis le 1er janvier de ce jour, ou
     * depuis l'embauche si elle est plus récente. Remis à zéro chaque 1er janvier.
     * e : l'agent, td : le type de document CONGE, ? : le jour.
     */
    static final String ACQUIS = """
            td.jours_conge_par_mois * GREATEST(0,
                EXTRACT(YEAR FROM age(?::date, GREATEST(date_trunc('year', ?::date)::date, e.date_embauche))) * 12
                + EXTRACT(MONTH FROM age(?::date, GREATEST(date_trunc('year', ?::date)::date, e.date_embauche))))""";

    private final JdbcTemplate jdbcTemplate;

    public CongeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Acquis et pris dans l'année de ce jour, et le rythme mensuel ; vide si l'agent n'existe pas. */
    public Optional<Map<String, Object>> droits(Integer employeId, LocalDate jour) {
        Date d = Date.valueOf(jour);
        return jdbcTemplate.queryForList("""
                SELECT td.jours_conge_par_mois AS jours_par_mois,
                       """ + ACQUIS + """
                 AS acquis,
                       COALESCE((SELECT SUM(c.jours) FROM conge c
                                 WHERE c.employe_id = e.id
                                   AND EXTRACT(YEAR FROM c.date_debut) = EXTRACT(YEAR FROM ?::date)), 0) AS pris
                FROM employe e
                CROSS JOIN type_document td
                WHERE e.id = ? AND td.code = 'CONGE' AND td.jours_conge_par_mois IS NOT NULL
                """, d, d, d, d, d, employeId).stream().findFirst();
    }

    /** Les congés de l'agent qui touchent cette année, du plus récent au plus ancien. */
    public List<Map<String, Object>> congesDeLAnnee(Integer employeId, int annee) {
        return jdbcTemplate.queryForList("""
                SELECT c.id, c.date_debut, c.date_fin, c.jours, d.reference_document
                FROM conge c
                LEFT JOIN document d ON d.id = c.document_id
                WHERE c.employe_id = ? AND EXTRACT(YEAR FROM c.date_debut) = ?
                ORDER BY c.date_debut DESC
                """, employeId, annee);
    }

    /** Fin du congé en cours aujourd'hui, s'il y en a un. */
    public Optional<LocalDate> enCongeJusquau(Integer employeId) {
        return jdbcTemplate.queryForList("""
                SELECT MAX(date_fin) FROM conge
                WHERE employe_id = ? AND CURRENT_DATE BETWEEN date_debut AND date_fin
                HAVING MAX(date_fin) IS NOT NULL
                """, LocalDate.class, employeId).stream().findFirst();
    }

    public boolean chevauche(Integer employeId, LocalDate debut, LocalDate fin) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM conge
                WHERE employe_id = ? AND date_debut <= ? AND date_fin >= ?
                """, Integer.class, employeId, Date.valueOf(fin), Date.valueOf(debut));
        return n != null && n > 0;
    }

    public int enregistrer(Integer employeId, Long documentId, LocalDate debut, LocalDate fin, int jours) {
        return jdbcTemplate.update("""
                INSERT INTO conge (employe_id, document_id, date_debut, date_fin, jours)
                VALUES (?, ?, ?, ?, ?)
                """, employeId, documentId, Date.valueOf(debut), Date.valueOf(fin), jours);
    }
}

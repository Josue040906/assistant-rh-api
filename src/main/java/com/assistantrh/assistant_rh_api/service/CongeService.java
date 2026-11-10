package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.CongeRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Congés : droits acquis mois après mois dans l'année (rythme lu dans les
 * données), jours pris, absence réelle. Les jours se comptent en jours
 * calendaires, du premier au dernier inclus.
 */
@Service
public class CongeService {
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CongeRepository congeRepository;
    private final SituationCarriereRepository situationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ActiviteService activiteService;

    public CongeService(
            CongeRepository congeRepository,
            SituationCarriereRepository situationRepository,
            UtilisateurRepository utilisateurRepository,
            ActiviteService activiteService
    ) {
        this.congeRepository = congeRepository;
        this.situationRepository = situationRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.activiteService = activiteService;
    }

    public static int joursCalendaires(LocalDate debut, LocalDate fin) {
        return (int) ChronoUnit.DAYS.between(debut, fin) + 1;
    }

    /** Droits, jours pris et solde de l'agent au jour donné, congé en cours et congés de l'année. */
    public Map<String, Object> analyser(Integer employeId, LocalDate jour) {
        if (employeId == null || employeId <= 0) {
            throw new IllegalArgumentException("L'identifiant de l'agent est invalide.");
        }
        LocalDate date = jour == null ? LocalDate.now() : jour;
        Map<String, Object> droits = congeRepository.droits(employeId, date)
                .orElseThrow(() -> new IllegalArgumentException(
                        "L'agent n'existe pas ou le rythme d'acquisition des congés n'est pas renseigné."));
        BigDecimal acquis = nombre(droits.get("acquis"));
        BigDecimal pris = nombre(droits.get("pris"));
        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("employeId", employeId);
        resultat.put("date", date);
        resultat.put("annee", date.getYear());
        resultat.put("joursParMois", nombre(droits.get("jours_par_mois")));
        resultat.put("acquis", acquis);
        resultat.put("pris", pris);
        resultat.put("solde", acquis.subtract(pris));
        resultat.put("enCongeJusquau", congeRepository.enCongeJusquau(employeId).orElse(null));
        resultat.put("conges", congeRepository.congesDeLAnnee(employeId, date.getYear()));
        return resultat;
    }

    /**
     * L'absence est enregistrée si le solde, au premier jour du congé, couvre
     * les jours demandés et qu'aucun autre congé ne la chevauche.
     */
    @Transactional
    public Map<String, Object> prendreConge(
            Integer employeId, Integer acteurId, LocalDate debut, LocalDate fin,
            Long documentId, String referenceActe
    ) {
        if (acteurId == null || acteurId <= 0 || !utilisateurRepository.estAgentSpersActif(acteurId)) {
            throw new IllegalArgumentException(
                    "L'acteur n'est pas un utilisateur actif autorisé du Service du Personnel.");
        }
        if (debut == null || fin == null || fin.isBefore(debut)) {
            throw new IllegalArgumentException("Les dates du congé sont invalides.");
        }
        if (employeId == null || !situationRepository.verrouillerEmploye(employeId)) {
            throw new IllegalArgumentException("L'agent demandé n'existe pas.");
        }
        if (debut.getYear() != fin.getYear()) {
            throw new IllegalArgumentException(
                    "Un congé ne peut pas chevaucher deux années : les droits repartent à zéro au 1er janvier.");
        }
        if (congeRepository.chevauche(employeId, debut, fin)) {
            throw new IllegalArgumentException("Un autre congé de l'agent couvre déjà une partie de ces dates.");
        }
        int jours = joursCalendaires(debut, fin);
        BigDecimal solde = (BigDecimal) analyser(employeId, debut).get("solde");
        if (solde.compareTo(BigDecimal.valueOf(jours)) < 0) {
            throw new IllegalArgumentException("Solde insuffisant : " + solde.stripTrailingZeros().toPlainString()
                    + " jours disponibles au " + debut.format(JOUR) + ", " + jours + " demandés.");
        }
        if (congeRepository.enregistrer(employeId, documentId, debut, fin, jours) != 1) {
            throw new IllegalStateException("Le congé n'a pas pu être enregistré.");
        }
        activiteService.enregistrer(acteurId, employeId, "CONGE",
                "Congé du " + debut.format(JOUR) + " au " + fin.format(JOUR) + " (" + jours + " jours)"
                        + (referenceActe == null ? "" : " — " + referenceActe));
        return Map.of("employeId", employeId, "jours", jours, "message", "Congé enregistré.");
    }

    private static BigDecimal nombre(Object valeur) {
        if (valeur instanceof BigDecimal decimal) {
            return decimal;
        }
        return valeur instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
    }
}

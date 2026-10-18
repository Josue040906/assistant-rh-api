package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AvancementService {

    private final SituationCarriereRepository situationRepository;
    private final EmployeRepository employeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ActiviteService activiteService;

    public AvancementService(
            SituationCarriereRepository situationRepository,
            EmployeRepository employeRepository,
            UtilisateurRepository utilisateurRepository,
            ActiviteService activiteService
    ) {
        this.situationRepository = situationRepository;
        this.employeRepository = employeRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.activiteService = activiteService;
    }

    public Optional<Map<String, Object>> analyser(Integer employeId, Integer acteurId) {
        verifierActeur(acteurId);
        if (employeId == null || employeId <= 0) {
            throw new IllegalArgumentException("L'identifiant de l'agent est invalide.");
        }
        return situationRepository.findSituationActuelle(employeId)
                .map(this::construireAnalyse);
    }

    @Transactional
    public Map<String, Object> confirmer(
            Integer employeId,
            Integer acteurId,
            LocalDate dateEffet
    ) {
        verifierActeur(acteurId);
        if (employeId == null || employeId <= 0) {
            throw new IllegalArgumentException("L'identifiant de l'agent est invalide.");
        }
        if (dateEffet == null || dateEffet.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "La date d'effet est obligatoire et ne peut pas être future."
            );
        }
        if (!situationRepository.verrouillerEmploye(employeId)) {
            throw new IllegalArgumentException("L'agent demandé n'existe pas.");
        }

        Map<String, Object> analyse = situationRepository
                .findSituationActuelle(employeId)
                .map(this::construireAnalyse)
                .orElseThrow(() -> new IllegalStateException(
                        "Aucune situation de carrière actuelle ne permet un avancement."
                ));
        if (!Boolean.TRUE.equals(analyse.get("eligible"))) {
            throw new IllegalStateException(String.join(
                    " ", lireMotifs(analyse.get("motifs"))
            ));
        }

        Map<String, Object> actuelle = lireMap(analyse.get("situationActuelle"));
        Map<String, Object> suivante = lireMap(analyse.get("echelonSuivant"));
        LocalDate debutActuel = convertirDate(actuelle.get("dateDebut"));
        if (!dateEffet.isAfter(debutActuel)) {
            throw new IllegalArgumentException(
                    "La date d'effet doit être postérieure au début de l'échelon actuel."
            );
        }
        Map<String, Object> agent = employeRepository.findById(employeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "L'agent demandé n'existe pas."
                ));

        Integer historiqueId = convertirEnInteger(actuelle.get("historiqueId"));
        int closed = situationRepository.fermerSituation(
                historiqueId, Date.valueOf(dateEffet.minusDays(1))
        );
        if (closed != 1) {
            throw new IllegalStateException(
                    "La période de carrière actuelle n'a pas pu être clôturée."
            );
        }
        int inserted = situationRepository.creerSituation(
                employeId,
                convertirEnInteger(actuelle.get("categorieId")),
                convertirEnInteger(suivante.get("echelonId")),
                Date.valueOf(dateEffet)
        );
        if (inserted != 1) {
            throw new IllegalStateException(
                    "La nouvelle période d'échelon n'a pas pu être créée."
            );
        }
        Map<String, Object> nouvelleSituation = situationRepository
                .findSituationActuelle(employeId)
                .orElseThrow(() -> new IllegalStateException(
                        "La nouvelle situation de carrière est introuvable."
                ));
        int recorded = situationRepository.creerAvancement(
                employeId,
                historiqueId,
                convertirEnInteger(nouvelleSituation.get("historique_id")),
                Date.valueOf(dateEffet)
        );
        if (recorded != 1) {
            throw new IllegalStateException(
                    "L'opération d'avancement n'a pas pu être enregistrée."
            );
        }

        activiteService.enregistrer(
                acteurId,
                employeId,
                "MODIFICATION",
                "Avancement d'échelon de l'agent "
                        + agent.get("matricule")
                        + " : "
                        + actuelle.get("classeLibelle")
                        + ", échelon "
                        + actuelle.get("echelonOrdre")
                        + " vers l'échelon "
                        + suivante.get("echelonOrdre")
                        + " à compter du "
                        + dateEffet
        );
        return Map.of(
                "employeId", employeId,
                "message", "Avancement d'échelon enregistré avec succès."
        );
    }

    private Map<String, Object> construireAnalyse(Map<String, Object> situation) {
        LocalDate debut = convertirDate(situation.get("date_debut"));
        LocalDate au = LocalDate.now();
        long moisEcoules = Math.max(0, ChronoUnit.MONTHS.between(debut, au));
        Object dureeValeur = situation.get("duree_min");
        Long dureeAnnees = dureeValeur instanceof Number number
                ? number.longValue()
                : null;
        Long dureeMois = dureeAnnees == null ? null : dureeAnnees * 12;
        Integer classeId = convertirEnInteger(situation.get("classe_id"));
        Integer echelonOrdre = convertirEnInteger(situation.get("echelon_ordre"));
        Optional<Map<String, Object>> next =
                situationRepository.findEchelonSuivant(classeId, echelonOrdre);

        List<String> motifs = new ArrayList<>();
        if (dureeMois == null) {
            motifs.add("La durée minimale requise n'est pas renseignée pour l'échelon actuel.");
        } else if (moisEcoules < dureeMois) {
            motifs.add("La durée minimale de " + dureeMois
                    + " mois dans l'échelon actuel n'est pas atteinte.");
        }
        if (next.isEmpty()) {
            motifs.add("Aucun échelon suivant n'existe dans la classe actuelle.");
        }
        boolean eligible = dureeMois != null
                && moisEcoules >= dureeMois
                && next.isPresent();

        Map<String, Object> actuelle = new LinkedHashMap<>();
        actuelle.put("historiqueId", situation.get("historique_id"));
        actuelle.put("categorieId", situation.get("categorie_id"));
        actuelle.put("categorieCode", situation.get("categorie_code"));
        actuelle.put("classeId", classeId);
        actuelle.put("classeLibelle", situation.get("classe_libelle"));
        actuelle.put("echelonId", situation.get("echelon_id"));
        actuelle.put("echelonOrdre", echelonOrdre);
        actuelle.put("dateDebut", debut);

        Map<String, Object> echelonSuivant = next.map(row -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("echelonId", row.get("id"));
            value.put("echelonOrdre", row.get("ordre"));
            value.put("classeId", row.get("classe_id"));
            value.put("classeLibelle", row.get("classe_libelle"));
            return value;
        }).orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("situationActuelle", actuelle);
        result.put("dateCalcul", au);
        result.put("moisEcoules", moisEcoules);
        result.put("dureeMinAnnees", dureeAnnees);
        result.put("dureeMinMois", dureeMois);
        result.put("echelonSuivant", echelonSuivant);
        result.put("eligible", eligible);
        result.put("motifs", motifs);
        return result;
    }

    private void verifierActeur(Integer acteurId) {
        if (acteurId == null || acteurId <= 0
                || !utilisateurRepository.estAgentSpersActif(acteurId)) {
            throw new IllegalArgumentException(
                    "L'acteur n'est pas un utilisateur actif autorisé du Service du Personnel."
            );
        }
    }

    private LocalDate convertirDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof java.util.Date date) {
            return new java.sql.Date(date.getTime()).toLocalDate();
        }
        if (value instanceof String date) {
            return LocalDate.parse(date);
        }
        throw new IllegalStateException("La date de début de carrière est invalide.");
    }

    private Integer convertirEnInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? null : Integer.valueOf(value.toString());
    }

    private Map<String, Object> lireMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalStateException("La proposition d'avancement est invalide.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalStateException("La proposition contient une clé invalide.");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private List<String> lireMotifs(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalStateException("Les motifs de blocage sont indisponibles.");
        }
        return list.stream().filter(String.class::isInstance)
                .map(String.class::cast).toList();
    }
}

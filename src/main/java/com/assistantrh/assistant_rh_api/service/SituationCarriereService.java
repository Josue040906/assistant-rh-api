package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.model.ProgressionCarriere;
import com.assistantrh.assistant_rh_api.model.SituationCarriereSnapshot;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class SituationCarriereService {

    private final SituationCarriereRepository situationCarriereRepository;
    private final EmployeRepository employeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ActiviteService activiteService;

    public SituationCarriereService(
            SituationCarriereRepository situationCarriereRepository,
            EmployeRepository employeRepository,
            UtilisateurRepository utilisateurRepository,
            ActiviteService activiteService
    ) {
        this.situationCarriereRepository = situationCarriereRepository;
        this.employeRepository = employeRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.activiteService = activiteService;
    }

    @Transactional
    public void ajouterSituationCarriere(
            Integer employeId,
            Integer acteurId,
            Integer echelonId,
            LocalDate dateDebut
    ) {
        if (employeId == null || employeId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'agent est invalide."
            );
        }
        if (acteurId == null || acteurId <= 0
                || !utilisateurRepository.estAgentSpersActif(acteurId)) {
            throw new IllegalArgumentException(
                    "L'acteur n'est pas un utilisateur actif autorisé du Service du Personnel."
            );
        }
        if (echelonId == null || echelonId <= 0) {
            throw new IllegalArgumentException(
                    "L'échelon est obligatoire."
            );
        }
        if (dateDebut == null || dateDebut.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "La date d'effet est obligatoire et ne peut pas être future."
            );
        }

        if (!situationCarriereRepository.verrouillerEmploye(employeId)) {
            throw new IllegalArgumentException(
                    "L'agent demandé n'existe pas."
            );
        }
        Map<String, Object> employe = employeRepository.findById(employeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "L'agent demandé n'existe pas."
                ));
        Map<String, Object> echelon = situationCarriereRepository
                .findEchelon(echelonId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "L'échelon demandé n'existe pas."
                ));

        Optional<Map<String, Object>> derniereSituation =
                situationCarriereRepository.findDerniereSituationOuverte(employeId);
        if (derniereSituation.isPresent()) {
            Map<String, Object> situation = derniereSituation.get();
            LocalDate dateDebutActuelle =
                    convertirEnLocalDate(situation.get("date_debut"));
            if (!dateDebut.isAfter(dateDebutActuelle)) {
                throw new IllegalArgumentException(
                        "La date d'effet doit être postérieure à la dernière situation de carrière."
                );
            }

            Object historiqueId = situation.get("historique_id");
            int ferme = situationCarriereRepository.fermerSituation(
                    convertirEnInteger(historiqueId),
                    Date.valueOf(dateDebut.minusDays(1))
            );
            if (ferme != 1) {
                throw new IllegalStateException(
                        "La situation de carrière précédente n'a pas pu être clôturée."
                );
            }
        }

        int cree = situationCarriereRepository.creerSituation(
                employeId,
                echelonId,
                Date.valueOf(dateDebut)
        );
        if (cree != 1) {
            throw new IllegalStateException(
                    "La nouvelle situation de carrière n'a pas pu être créée."
            );
        }

        String typeAction = derniereSituation.isPresent()
                ? "MODIFICATION"
                : "CREATION";
        activiteService.enregistrer(
                acteurId,
                employeId,
                typeAction,
                "Ajout d'une situation de carrière pour l'agent "
                        + employe.get("matricule")
                        + " : "
                        + echelon.get("classe_libelle")
                        + ", échelon "
                        + echelon.get("echelon_ordre")
                        + " à compter du "
                        + dateDebut
        );
    }

    public Optional<Map<String, Object>> obtenirSituationActuelle(Integer employeId) {
        if (employeId == null) {
            return Optional.empty();
        }

        return situationCarriereRepository
                .findSituationActuelle(employeId);
    }
    public List<Map<String, Object>> obtenirHistorique(Integer employeId) {
        if (employeId == null) {
            return List.of();
        }

        return situationCarriereRepository.findHistorique(employeId);
    }

    public Optional<Map<String, Object>> obtenirEchelonSuivant(Integer classeId, Integer ordreActuel) {
        if (classeId == null || ordreActuel == null) {
            return Optional.empty();
        }

        return situationCarriereRepository.findEchelonSuivant(
                classeId,
                ordreActuel
        );
    }
    public Optional<Map<String, Object>> obtenirClasseSuivante(Integer ordreActuel) {
        if (ordreActuel == null) {
            return Optional.empty();
        }

        return situationCarriereRepository.findClasseSuivante(
                ordreActuel
        );
    }

    public Optional<Map<String, Object>> analyserEvolutionCarriere(Integer employeId) {
        if (employeId == null) {
            return Optional.empty();
        }

        Optional<Map<String, Object>> situationActuelleOpt =
                obtenirSituationActuelle(employeId);
        if (situationActuelleOpt.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> situationActuelle =
                situationActuelleOpt.get();
        Map<String, Object> situationActuelleApi =
                formaterSituationActuelle(situationActuelle);

        LocalDate dateDebut = convertirEnLocalDate(situationActuelle.get("date_debut"));
        LocalDate dateCalcul = LocalDate.now();
        long moisTotal = ChronoUnit.MONTHS.between(dateDebut, dateCalcul);
        boolean eligibiliteDeterminee = situationActuelle.get("duree_min") != null;
        Long dureeMinAnnees = eligibiliteDeterminee
                ? convertirEnLong(situationActuelle.get("duree_min"))
                : null;
        Long dureeRequiseMois = dureeMinAnnees == null
                ? null
                : dureeMinAnnees * 12;
        Boolean eligible = eligibiliteDeterminee
                ? moisTotal >= dureeRequiseMois
                : null;

        Map<String, Object> anciennete = new LinkedHashMap<>();
        anciennete.put("dateDebut", dateDebut);
        anciennete.put("dateCalcul", dateCalcul);
        anciennete.put("annees", moisTotal / 12);
        anciennete.put("mois", moisTotal % 12);
        anciennete.put("moisTotal", moisTotal);

        Map<String, Object> ancienneteRequise = new LinkedHashMap<>();
        ancienneteRequise.put("dureeMinAnnees", dureeMinAnnees);
        ancienneteRequise.put("dureeMinMois", dureeRequiseMois);
        ancienneteRequise.put("moisTotal", dureeRequiseMois);

        Optional<Map<String, Object>> echelonSuivant =
                obtenirEchelonSuivant(
                        convertirEnInteger(situationActuelle.get("classe_id")),
                        convertirEnInteger(situationActuelle.get("echelon_ordre"))
                );

        String typeEvolution;
        Map<String, Object> situationSuivante = null;
        String classeSuivanteLibelle = null;

        if (echelonSuivant.isPresent()) {
            typeEvolution = "ECHELON";
            situationSuivante = formaterSituationSuivante(echelonSuivant.get());
        } else {
            Optional<Map<String, Object>> classeSuivanteOpt =
                    obtenirClasseSuivante(
                            convertirEnInteger(situationActuelle.get("classe_ordre"))
                    );
            if (classeSuivanteOpt.isPresent()) {
                classeSuivanteLibelle =
                        classeSuivanteOpt.get().get("libelle").toString();
                Optional<Map<String, Object>> premierEchelon =
                        situationCarriereRepository.findPremierEchelon(
                                convertirEnInteger(classeSuivanteOpt.get().get("id"))
                        );
                if (premierEchelon.isPresent()) {
                    typeEvolution = "CLASSE";
                    situationSuivante = formaterSituationSuivante(premierEchelon.get());
                } else {
                    typeEvolution = "AUCUNE";
                }
            } else {
                typeEvolution = "AUCUNE";
            }
        }

        Map<String, Object> evolution = new LinkedHashMap<>();
        evolution.put("type", typeEvolution);
        evolution.put("classeActuelle", situationActuelleApi.get("classeLibelle"));
        evolution.put("echelonActuel", situationActuelleApi.get("echelonOrdre"));
        evolution.put(
                "classeSuivante",
                situationSuivante == null
                        ? classeSuivanteLibelle
                        : situationSuivante.get("classeLibelle")
        );
        evolution.put("echelonSuivant", situationSuivante == null
                ? null
                : situationSuivante.get("echelonOrdre"));

        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("employeId", employeId);
        resultat.put("situationActuelle", situationActuelleApi);
        resultat.put("situationSuivante", situationSuivante);
        resultat.put(
                "historique",
                obtenirHistorique(employeId).stream()
                        .map(this::formaterEntreeHistorique)
                        .toList()
        );
        resultat.put("ancienneteActuelle", anciennete);
        resultat.put("ancienneteRequise", ancienneteRequise);
        resultat.put("dureeMin", dureeMinAnnees);
        resultat.put("eligible", eligible);
        resultat.put("eligibiliteDeterminee", eligibiliteDeterminee);
        resultat.put("evolution", evolution);
        return Optional.of(resultat);
    }

    public Optional<ProgressionCarriere> obtenirProgressionCarriere(
            Integer employeId
    ) {
        return analyserEvolutionCarriere(employeId).map(analyse -> {
            Map<String, Object> situationActuelle =
                    convertirEnMap(analyse.get("situationActuelle"));
            Map<String, Object> situationSuivante =
                    analyse.get("situationSuivante") == null
                            ? null
                            : convertirEnMap(analyse.get("situationSuivante"));
            Map<String, Object> evolution =
                    convertirEnMap(analyse.get("evolution"));

            return new ProgressionCarriere(
                    creerSnapshot(situationActuelle),
                    situationSuivante == null
                            ? null
                            : creerSnapshot(situationSuivante),
                    Boolean.TRUE.equals(analyse.get("eligibiliteDeterminee")),
                    analyse.get("eligible") instanceof Boolean resultatEligible
                            ? resultatEligible
                            : null,
                    Objects.requireNonNull(
                            (String) evolution.get("type"),
                            "Le type d'évolution de carrière est absent."
                    )
            );
        });
    }

    private SituationCarriereSnapshot creerSnapshot(
            Map<String, Object> situation
    ) {
        return new SituationCarriereSnapshot(
                convertirEnInteger(situation.get("echelonId")),
                convertirEnInteger(situation.get("echelonOrdre")),
                convertirEnInteger(situation.get("classeId")),
                Objects.requireNonNull(
                        (String) situation.get("classeLibelle"),
                        "Le libellé de classe de carrière est absent."
                ),
                convertirEnInteger(situation.get("classeOrdre")),
                convertirEnInteger(situation.get("dureeMin")),
                situation.get("dateDebut") == null
                        ? null
                        : convertirEnLocalDate(situation.get("dateDebut"))
        );
    }

    private Map<String, Object> convertirEnMap(Object valeur) {
        if (!(valeur instanceof Map<?, ?> carte)) {
            throw new IllegalStateException(
                    "L'analyse de carrière a retourné une situation invalide."
            );
        }

        Map<String, Object> resultat = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entree : carte.entrySet()) {
            if (!(entree.getKey() instanceof String cle)) {
                throw new IllegalStateException(
                        "L'analyse de carrière a retourné une clé invalide."
                );
            }
            resultat.put(cle, entree.getValue());
        }
        return resultat;
    }

    private Map<String, Object> formaterSituationActuelle(Map<String, Object> situation) {
        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("historiqueId", situation.get("historique_id"));
        resultat.put("employeId", situation.get("employe_id"));
        resultat.put("dateDebut", situation.get("date_debut"));
        resultat.put("echelonId", situation.get("echelon_id"));
        resultat.put("echelonOrdre", situation.get("echelon_ordre"));
        resultat.put("dureeMin", situation.get("duree_min"));
        resultat.put("classeId", situation.get("classe_id"));
        resultat.put("classeLibelle", situation.get("classe_libelle"));
        resultat.put("classeOrdre", situation.get("classe_ordre"));
        return resultat;
    }

    private Map<String, Object> formaterSituationSuivante(Map<String, Object> situation) {
        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("echelonId", situation.get("id"));
        resultat.put("echelonOrdre", situation.get("ordre"));
        resultat.put("dureeMin", situation.get("duree_min"));
        resultat.put("classeId", situation.get("classe_id"));
        resultat.put("classeLibelle", situation.get("classe_libelle"));
        resultat.put("classeOrdre", situation.get("classe_ordre"));
        return resultat;
    }

    private Map<String, Object> formaterEntreeHistorique(
            Map<String, Object> entree
    ) {
        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("historiqueId", entree.get("id"));
        resultat.put("employeId", entree.get("employe_id"));
        resultat.put("dateDebut", entree.get("date_debut"));
        resultat.put("dateFin", entree.get("date_fin"));
        resultat.put("echelonId", entree.get("echelon_id"));
        resultat.put("echelonOrdre", entree.get("echelon_ordre"));
        resultat.put("dureeMin", entree.get("duree_min"));
        resultat.put("classeId", entree.get("classe_id"));
        resultat.put("classeLibelle", entree.get("classe_libelle"));
        resultat.put("classeOrdre", entree.get("classe_ordre"));
        return resultat;
    }

    private LocalDate convertirEnLocalDate(Object valeur) {
        if (valeur instanceof LocalDate date) {
            return date;
        }
        if (valeur instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (valeur instanceof java.util.Date date) {
            return new java.sql.Date(date.getTime()).toLocalDate();
        }
        if (valeur instanceof String date) {
            return LocalDate.parse(date);
        }
        throw new IllegalArgumentException("Date de début de carrière invalide : " + valeur);
    }

    private Integer convertirEnInteger(Object valeur) {
        if (valeur == null) {
            return null;
        }
        if (valeur instanceof Number nombre) {
            return nombre.intValue();
        }
        return Integer.valueOf(valeur.toString());
    }

    private long convertirEnLong(Object valeur) {
        if (valeur instanceof Number nombre) {
            return nombre.longValue();
        }
        return Long.parseLong(valeur.toString());
    }
}
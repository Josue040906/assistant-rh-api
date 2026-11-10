package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Titularisation : un agent contractuel ou stagiaire devient fonctionnaire. Il
 * garde sa catégorie ; son indice actuel guide le grade de fonctionnaire
 * proposé — celui de même indice s'il existe, sinon le premier d'indice
 * supérieur — et un humain confirme ou choisit un autre grade.
 */
@Service
public class TitularisationService {
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SituationCarriereRepository situationRepository;
    private final EmployeRepository employeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ActiviteService activiteService;
    private final DemandesLieesService demandesLiees;

    public TitularisationService(
            SituationCarriereRepository situationRepository,
            EmployeRepository employeRepository,
            UtilisateurRepository utilisateurRepository,
            ActiviteService activiteService,
            DemandesLieesService demandesLiees
    ) {
        this.situationRepository = situationRepository;
        this.employeRepository = employeRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.activiteService = activiteService;
        this.demandesLiees = demandesLiees;
    }

    /** Le grade proposé : indice égal s'il existe, sinon le premier supérieur ; à défaut, le plus haut. */
    static Optional<Map<String, Object>> gradePropose(List<Map<String, Object>> options, Integer indiceActuel) {
        if (options.isEmpty()) {
            return Optional.empty();
        }
        if (indiceActuel == null) {
            return Optional.of(options.get(0));
        }
        return Optional.of(options.stream()
                .filter(option -> entier(option.get("indice")) >= indiceActuel)
                .findFirst()
                .orElse(options.get(options.size() - 1)));
    }

    public Map<String, Object> analyser(Integer employeId) {
        if (employeId == null || employeId <= 0) {
            throw new IllegalArgumentException("L'identifiant de l'agent est invalide.");
        }
        Map<String, Object> employe = employeRepository.findById(employeId)
                .orElseThrow(() -> new IllegalArgumentException("L'agent demandé n'existe pas."));
        Optional<Map<String, Object>> situation = situationRepository.findSituationActuelle(employeId);
        Integer categorieId = situation.map(s -> entier(s.get("categorie_id"))).orElse(null);
        List<Map<String, Object>> options = categorieId == null
                ? List.of() : situationRepository.findGradesFonctionnaire(categorieId);
        Integer indiceActuel = situation.map(s -> entier(s.get("indice"))).orElse(null);

        String raison = null;
        if ("Fonctionnaire".equals(employe.get("type_emploi"))) {
            raison = "L'agent est déjà fonctionnaire.";
        } else if (employe.get("date_retraite") != null) {
            raison = "L'agent est parti à la retraite.";
        } else if (situation.isEmpty()) {
            raison = "L'agent n'a pas de situation de carrière en cours.";
        } else if (options.isEmpty()) {
            raison = "La catégorie de l'agent n'a pas de grade de fonctionnaire dans la grille indiciaire.";
        }

        Map<String, Object> actuelle = new LinkedHashMap<>();
        situation.ifPresent(s -> {
            actuelle.put("echelonId", s.get("echelon_id"));
            actuelle.put("gradeCode", s.get("grade_code"));
            actuelle.put("gradeLibelle", s.get("grade_libelle"));
            actuelle.put("indice", s.get("indice"));
            actuelle.put("categorie", s.get("categorie_code"));
            actuelle.put("dateDebut", s.get("date_debut"));
        });

        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("employeId", employeId);
        resultat.put("typeEmploi", employe.get("type_emploi"));
        resultat.put("possible", raison == null);
        resultat.put("raison", raison);
        resultat.put("situationActuelle", actuelle);
        resultat.put("options", options);
        resultat.put("suggestion", raison == null ? gradePropose(options, indiceActuel).orElse(null) : null);
        return resultat;
    }

    /** Le passage au statut de fonctionnaire, par le bouton direct ou par une demande appliquée. */
    @Transactional
    public Map<String, Object> titulariser(
            Integer employeId, Integer acteurId, LocalDate dateEffet, Integer echelonId, String referenceActe
    ) {
        if (acteurId == null || acteurId <= 0 || !utilisateurRepository.estAgentSpersActif(acteurId)) {
            throw new IllegalArgumentException(
                    "L'acteur n'est pas un utilisateur actif autorisé du Service du Personnel.");
        }
        if (dateEffet == null || dateEffet.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("La date d'effet est obligatoire et ne peut pas être future.");
        }
        if (employeId == null || !situationRepository.verrouillerEmploye(employeId)) {
            throw new IllegalArgumentException("L'agent demandé n'existe pas.");
        }
        Map<String, Object> analyse = analyser(employeId);
        if (!Boolean.TRUE.equals(analyse.get("possible"))) {
            throw new IllegalArgumentException(String.valueOf(analyse.get("raison")));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> options = (List<Map<String, Object>>) analyse.get("options");
        Map<String, Object> grade = options.stream()
                .filter(option -> Objects.equals(entier(option.get("echelon_id")), echelonId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Le grade choisi n'est pas un grade de fonctionnaire de la catégorie de l'agent."));

        Map<String, Object> situation = situationRepository.findSituationActuelle(employeId).orElseThrow();
        LocalDate debutActuel = ((java.sql.Date) situation.get("date_debut")).toLocalDate();
        if (!dateEffet.isAfter(debutActuel)) {
            throw new IllegalArgumentException("La date d'effet doit suivre le début de la situation actuelle ("
                    + debutActuel.format(JOUR) + ").");
        }
        Integer historiqueAvant = entier(situation.get("historique_id"));
        Integer categorieId = entier(situation.get("categorie_id"));
        if (situationRepository.fermerSituation(historiqueAvant, Date.valueOf(dateEffet.minusDays(1))) != 1
                || situationRepository.passerAuTypeDuGrade(employeId, echelonId) != 1
                || situationRepository.creerSituation(employeId, categorieId, echelonId, Date.valueOf(dateEffet)) != 1) {
            throw new IllegalStateException("La titularisation n'a pas pu être enregistrée.");
        }
        Integer historiqueApres = situationRepository.findSituationActuelle(employeId)
                .map(s -> entier(s.get("historique_id"))).orElseThrow();
        situationRepository.creerAvancement(employeId, historiqueAvant, historiqueApres,
                Date.valueOf(dateEffet), referenceActe, "Titularisation");

        Object matricule = employeRepository.findById(employeId).map(e -> e.get("matricule")).orElse(employeId);
        activiteService.enregistrer(acteurId, employeId, "TITULARISATION",
                "Titularisation de l'agent " + matricule + " : " + situation.get("grade_code")
                        + " (indice " + situation.get("indice") + ") vers " + grade.get("code")
                        + " (indice " + grade.get("indice") + ") à compter du " + dateEffet.format(JOUR)
                        + (referenceActe == null ? "" : " — " + referenceActe));
        demandesLiees.titularisation(employeId, acteurId);
        return Map.of("employeId", employeId, "message", "Titularisation enregistrée.");
    }

    private static Integer entier(Object valeur) {
        if (valeur instanceof Number nombre) {
            return nombre.intValue();
        }
        return valeur == null ? null : Integer.valueOf(valeur.toString());
    }
}

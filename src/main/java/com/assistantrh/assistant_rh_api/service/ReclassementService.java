package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.DiplomeRepository;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import com.assistantrh.assistant_rh_api.repository.ReclassementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class ReclassementService {

    private final DiplomeRepository diplomeRepository;
    private final SituationCarriereRepository situationRepository;
    private final EmployeRepository employeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ActiviteService activiteService;
    private final ReclassementRepository reclassementRepository;

    public ReclassementService(
            DiplomeRepository diplomeRepository,
            SituationCarriereRepository situationRepository,
            EmployeRepository employeRepository,
            UtilisateurRepository utilisateurRepository,
            ActiviteService activiteService
    ) {
        this(diplomeRepository, situationRepository, employeRepository,
                utilisateurRepository, activiteService, null);
    }

    @Autowired
    public ReclassementService(
            DiplomeRepository diplomeRepository,
            SituationCarriereRepository situationRepository,
            EmployeRepository employeRepository,
            UtilisateurRepository utilisateurRepository,
            ActiviteService activiteService,
            ReclassementRepository reclassementRepository
    ) {
        this.diplomeRepository = diplomeRepository;
        this.situationRepository = situationRepository;
        this.employeRepository = employeRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.activiteService = activiteService;
        this.reclassementRepository = reclassementRepository;
    }

    public Map<String, Object> proposer(Integer employeId, Integer acteurId) {
        verifierSpers(acteurId);
        verifierEmployeId(employeId);
        return formaterProposition(construireEtat(employeId));
    }

    @Transactional
    public Map<String, Object> confirmer(
            Integer employeId,
            Integer acteurId,
            LocalDate dateEffet
    ) {
        verifierSpers(acteurId);
        verifierEmployeId(employeId);
        if (!situationRepository.verrouillerEmploye(employeId)) {
            throw erreur("EMPLOYE_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "L'agent demandé n'existe pas.");
        }

        EtatReclassement etat = construireEtat(employeId);
        if (!etat.changementNecessaire()) {
            return formaterResultat(etat, false, LocalDate.now());
        }

        LocalDate effet = dateEffet == null ? LocalDate.now() : dateEffet;
        if (effet.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "La date d'effet ne peut pas être future."
            );
        }

        Optional<Map<String, Object>> situation =
                situationRepository.findSituationActuelle(employeId);
        Integer historiqueAvantId = situation.map(row ->
                convertirEnInteger(row.get("historique_id"))).orElse(null);
        Integer historiqueApresId = historiqueAvantId;
        if (situation.isEmpty()) {
            Optional<Map<String, Object>> ouverte =
                    situationRepository.findDerniereSituationOuverte(employeId);
            if (ouverte.isPresent()) {
                LocalDate debutOuvert = convertirDate(ouverte.get().get("date_debut"));
                if (debutOuvert.isAfter(effet)) {
                    throw erreur("CAREER_HISTORY_INCONSISTENT",
                            HttpStatus.CONFLICT,
                            "Une période de carrière future empêche d'enregistrer le reclassement à cette date.");
                }
            }
        } else {
            Map<String, Object> actuelle = situation.get();
            Integer categorieHistorique =
                    convertirEnInteger(actuelle.get("categorie_id"));
            if (categorieHistorique != null
                    && !categorieHistorique.equals(etat.categorieActuelleId())) {
                throw erreur("CAREER_HISTORY_INCONSISTENT",
                        HttpStatus.CONFLICT,
                        "La catégorie de la période de carrière active ne correspond pas à celle de l'agent.");
            }

            LocalDate debut = convertirDate(actuelle.get("date_debut"));
            if (effet.isBefore(debut)) {
                throw erreur("CAREER_DATE_CONFLICT", HttpStatus.CONFLICT,
                        "La date d'effet ne peut pas précéder le début de la période de carrière active.");
            }
            Integer historiqueId =
                    convertirEnInteger(actuelle.get("historique_id"));
            if (effet.equals(debut)) {
                if (situationRepository.modifierSituationReclassement(
                        historiqueId, etat.categorieCibleId(), etat.echelonCibleId()
                ) != 1) {
                    throw erreur("CAREER_HISTORY_UPDATE_FAILED",
                            HttpStatus.CONFLICT,
                            "La catégorie de l'historique de carrière n'a pas pu être mise à jour.");
                }
            } else {
                if (situationRepository.enregistrerCategorieSituation(
                        historiqueId, etat.categorieActuelleId()
                ) != 1) {
                    throw erreur("CAREER_HISTORY_UPDATE_FAILED",
                            HttpStatus.CONFLICT,
                            "La catégorie historique n'a pas pu être préservée avant le reclassement.");
                }
                if (situationRepository.fermerSituation(
                        historiqueId, Date.valueOf(effet.minusDays(1))
                ) != 1) {
                    throw erreur("CAREER_HISTORY_UPDATE_FAILED",
                            HttpStatus.CONFLICT,
                            "La période de carrière active n'a pas pu être clôturée.");
                }
            }
        }

        if (employeRepository.updateCategorie(
                employeId, etat.categorieActuelleId(), etat.categorieCibleId()
        ) != 1) {
            throw erreur("CLASSIFICATION_UPDATE_FAILED",
                    HttpStatus.CONFLICT,
                    "La catégorie de l'agent a changé pendant le traitement; le reclassement n'a pas été appliqué.");
        }

        if (situation.isEmpty()
                || !effet.equals(convertirDate(situation.get().get("date_debut")))) {
            if (situationRepository.creerSituation(
                    employeId,
                    etat.categorieCibleId(),
                    etat.echelonCibleId(),
                    Date.valueOf(effet)
            ) != 1) {
                throw erreur("CAREER_HISTORY_UPDATE_FAILED",
                        HttpStatus.CONFLICT,
                        "La nouvelle période de carrière n'a pas pu être créée.");
            }
            historiqueApresId = situationRepository.findSituationActuelle(employeId)
                    .map(row -> convertirEnInteger(row.get("historique_id"))).orElse(null);
        }

        Integer activiteId = activiteService.enregistrer(
                acteurId, employeId, "MODIFICATION",
                construireDescriptionAudit(etat, effet)
        );
        if (reclassementRepository != null) {
            int inserted = reclassementRepository.enregistrer(
                    employeId, activiteId,
                    convertirEnInteger(etat.diplome().get("employe_diplome_id")),
                    historiqueAvantId, historiqueApresId,
                    etat.categorieActuelleId(), etat.categorieCibleId(),
                    etat.corpsActuelId(), etat.corpsCibleId(), Date.valueOf(effet)
            );
            if (inserted != 1) {
                throw erreur("RECLASSEMENT_HISTORY_WRITE_FAILED",
                        HttpStatus.CONFLICT,
                        "L'historique détaillé du reclassement n'a pas pu être enregistré.");
            }
        }
        return formaterResultat(etat, true, effet);
    }

    public List<Map<String, Object>> obtenirHistorique(Integer employeId, Integer acteurId) {
        verifierSpers(acteurId);
        verifierEmployeId(employeId);
        if (!situationRepository.verrouillerEmploye(employeId)) {
            throw erreur("EMPLOYE_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "L'agent demandé n'existe pas.");
        }
        if (reclassementRepository == null) {
            return List.of();
        }
        return reclassementRepository.historiqueEmploye(employeId);
    }

    public List<Map<String, Object>> obtenirRegles(Integer acteurId) {
        verifierChef(acteurId);
        return diplomeRepository.findReclassementRules();
    }

    public List<Map<String, Object>> obtenirCiblesCarriere(Integer acteurId) {
        verifierChef(acteurId);
        return diplomeRepository.findCareerTargets();
    }

    @Transactional
    public void configurerOrdreNiveau(
            Integer acteurId,
            Integer niveauId,
            Integer ordreAcademique
    ) {
        verifierChef(acteurId);
        if (niveauId == null || ordreAcademique == null || ordreAcademique <= 0) {
            throw new IllegalArgumentException(
                    "Le niveau et son ordre académique positif sont obligatoires."
            );
        }
        if (diplomeRepository.setOrdreAcademique(niveauId, ordreAcademique) != 1) {
            throw new IllegalArgumentException(
                    "Le niveau de diplôme demandé n'existe pas."
            );
        }
        activiteService.enregistrer(
                acteurId,
                null,
                "MODIFICATION",
                "Configuration de l'ordre académique " + ordreAcademique
                        + " pour le niveau de diplôme " + niveauId
        );
    }

    @Transactional
    public void configurerRegle(
            Integer acteurId,
            Integer niveauId,
            Integer ordreAcademique,
            Integer classeId,
            Integer echelonId,
            String referenceJuridique,
            Boolean actif
    ) {
        verifierChef(acteurId);
        if (niveauId == null || ordreAcademique == null || ordreAcademique <= 0
                || classeId == null || echelonId == null) {
            throw new IllegalArgumentException(
                    "Le niveau, son ordre académique positif, la classe et l'échelon sont obligatoires."
            );
        }
        if (referenceJuridique == null || referenceJuridique.isBlank()
                || referenceJuridique.trim().length() > 1000) {
            throw new IllegalArgumentException(
                    "La référence juridique est obligatoire (1000 caractères maximum)."
            );
        }
        if (!diplomeRepository.niveauExiste(niveauId)) {
            throw new IllegalArgumentException(
                    "Le niveau de diplôme demandé n'existe pas."
            );
        }
        if (diplomeRepository.setOrdreAcademique(niveauId, ordreAcademique) != 1) {
            throw new IllegalArgumentException(
                    "Le niveau de diplôme demandé n'existe pas."
            );
        }
        if (!diplomeRepository.echelonAppartientClasse(echelonId, classeId)) {
            throw new IllegalArgumentException(
                    "L'échelon sélectionné n'appartient pas à la classe indiquée."
            );
        }
        diplomeRepository.upsertReclassementRule(
                niveauId,
                classeId,
                echelonId,
                referenceJuridique.trim(),
                !Boolean.FALSE.equals(actif)
        );
        activiteService.enregistrer(
                acteurId,
                null,
                "MODIFICATION",
                "Configuration de la règle de reclassement du niveau "
                        + niveauId + " vers classe " + classeId
                        + ", échelon " + echelonId
        );
    }

    private EtatReclassement construireEtat(Integer employeId) {
        Map<String, Object> employe = employeRepository.findById(employeId)
                .orElseThrow(() -> erreur("EMPLOYE_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "L'agent demandé n'existe pas."));
        List<Map<String, Object>> diplomes =
                diplomeRepository.findDiplomesValidesPourReclassement(employeId);
        if (diplomes.isEmpty()) {
            throw erreur("NO_VALID_DIPLOMA", HttpStatus.UNPROCESSABLE_ENTITY,
                    "L'agent ne possède aucun diplôme exploitable et valide.");
        }
        if (diplomes.stream().anyMatch(
                diplome -> diplome.get("ordre_academique") == null
        )) {
            throw erreur("ACADEMIC_ORDER_MISSING",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "L'ordre académique d'au moins un diplôme valide n'est pas défini; le diplôme de référence ne peut pas être déterminé.");
        }

        Map<String, Object> reference = diplomes.stream()
                .max(comparateurDiplome())
                .orElseThrow();
        if (reference.get("categorie_id") == null) {
            throw erreur("TARGET_CATEGORY_MISSING",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Le niveau du diplôme de référence ne possède pas de catégorie associée.");
        }
        if (reference.get("corps_id") == null) {
            throw erreur("TARGET_CORPS_MISSING",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "La catégorie cible du diplôme de référence ne possède pas de corps associé.");
        }

        Integer niveauReferenceId = convertirEnInteger(reference.get("niveau_id"));
        Map<String, Object> regle = diplomeRepository
                .findRegleActiveReclassement(niveauReferenceId)
                .orElseThrow(() -> erreur("RECLASSIFICATION_RULE_MISSING",
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Aucune règle active de reclassement n'est configurée pour le niveau du diplôme de référence."));
        Integer classeCibleId = convertirEnInteger(regle.get("classe_id"));
        Integer echelonCibleId = convertirEnInteger(regle.get("echelon_id"));
        if (classeCibleId == null || echelonCibleId == null) {
            throw erreur("RECLASSIFICATION_RULE_INVALID",
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "La règle de reclassement active ne définit pas une classe et un échelon valides.");
        }

        Map<String, Object> situation = situationRepository
                .findSituationActuelle(employeId).orElse(null);
        Integer categorieActuelleId =
                convertirEnInteger(employe.get("categorie_id"));
        Integer corpsActuelId = convertirEnInteger(employe.get("corps_id"));
        Integer categorieCibleId =
                convertirEnInteger(reference.get("categorie_id"));
        Integer corpsCibleId = convertirEnInteger(reference.get("corps_id"));
        if (categorieActuelleId != null
                && categorieActuelleId.equals(categorieCibleId)
                && !objetsEgaux(corpsActuelId, corpsCibleId)) {
            throw erreur("CLASSIFICATION_INCONSISTENT", HttpStatus.CONFLICT,
                    "Le corps dérivé de la catégorie actuelle ne correspond pas au corps de la catégorie cible.");
        }

        return new EtatReclassement(
                employe, reference, situation, regle,
                categorieActuelleId, corpsActuelId,
                categorieCibleId, corpsCibleId,
                classeCibleId, echelonCibleId
        );
    }

    private Comparator<Map<String, Object>> comparateurDiplome() {
        return Comparator
                .comparing(
                        (Map<String, Object> row) ->
                                convertirEnInteger(row.get("ordre_academique"))
                )
                .thenComparing(
                        row -> lireDateNullable(row.get("date_obtention")),
                        Comparator.nullsFirst(Comparator.naturalOrder())
                )
                .thenComparing(
                        row -> convertirEnInteger(row.get("employe_diplome_id"))
                );
    }

    private Map<String, Object> formaterProposition(EtatReclassement etat) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean changement = etat.changementNecessaire();
        result.put("employeId", etat.employeId());
        result.put("diplomeRetenu", formaterDiplome(etat.diplome()));
        result.put("situationActuelle", formaterSituation(etat));
        result.put("situationProposee", formaterSituationCible(etat));
        result.put("regleAppliquee", formaterRegleCible(etat));
        result.put("eligible", changement);
        result.put("resultCode", changement ? "RECLASSIFICATION_REQUIRED"
                : "ALREADY_CLASSIFIED");
        result.put("motifs", changement ? List.of()
                : List.of("L'agent est déjà classé conformément à son diplôme de référence."));
        return result;
    }

    private Map<String, Object> formaterResultat(
            EtatReclassement etat,
            boolean changement,
            LocalDate effet
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("changed", changement);
        result.put("resultCode", changement ? "RECLASSIFIED"
                : "ALREADY_CLASSIFIED");
        result.put("message", changement
                ? "Le reclassement de l'agent a été effectué."
                : "Aucun reclassement nécessaire : l'agent est déjà classé conformément à son diplôme de référence.");
        result.put("employeId", etat.employeId());
        result.put("matricule", etat.employe().get("matricule"));
        result.put("diplomeReference", formaterDiplome(etat.diplome()));
        result.put("ancienneCategorie", classification(
                etat.categorieActuelleId(),
                etat.employe().get("categorie"),
                etat.corpsActuelId(),
                etat.employe().get("corps"),
                etat.employe().get("corps_libelle")
        ));
        result.put("nouvelleCategorie", classification(
                etat.categorieCibleId(),
                etat.diplome().get("categorie_code"),
                etat.corpsCibleId(),
                etat.diplome().get("corps_code"),
                etat.diplome().get("corps_libelle")
        ));
        result.put("nouvelleSituationCarriere", formaterRegleCible(etat));
        result.put("dateEffet", effet);
        result.put("dateOperation", LocalDateTime.now());
        return result;
    }

    private Map<String, Object> formaterDiplome(Map<String, Object> diplome) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("employeDiplomeId", diplome.get("employe_diplome_id"));
        result.put("intitule", diplome.get("intitule"));
        result.put("niveauId", diplome.get("niveau_id"));
        result.put("niveau", diplome.get("niveau"));
        result.put("ordreAcademique", diplome.get("ordre_academique"));
        result.put("dateObtention", diplome.get("date_obtention"));
        return result;
    }

    private Map<String, Object> formaterSituation(EtatReclassement etat) {
        Map<String, Object> result = classification(
                etat.categorieActuelleId(),
                etat.employe().get("categorie"),
                etat.corpsActuelId(),
                etat.employe().get("corps"),
                etat.employe().get("corps_libelle")
        );
        if (etat.situation() != null) {
            result.put("historiqueId", etat.situation().get("historique_id"));
            result.put("echelonId", etat.situation().get("echelon_id"));
            result.put("dateDebut", etat.situation().get("date_debut"));
        }
        return result;
    }

    private Map<String, Object> formaterSituationCible(EtatReclassement etat) {
        Map<String, Object> result = classification(
                etat.categorieCibleId(),
                etat.diplome().get("categorie_code"),
                etat.corpsCibleId(),
                etat.diplome().get("corps_code"),
                etat.diplome().get("corps_libelle")
        );
        result.putAll(formaterRegleCible(etat));
        return result;
    }

    private Map<String, Object> formaterRegleCible(EtatReclassement etat) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("classeId", etat.classeCibleId());
        result.put("classe", etat.regle().get("classe"));
        result.put("echelonId", etat.echelonCibleId());
        result.put("echelon", etat.regle().get("echelon"));
        result.put("referenceJuridique", etat.regle().get("reference_juridique"));
        return result;
    }

    private Map<String, Object> classification(
            Integer categorieId,
            Object categorieCode,
            Integer corpsId,
            Object corpsCode,
            Object corpsLibelle
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categorieId", categorieId);
        result.put("categorieCode", categorieCode);
        result.put("corpsId", corpsId);
        result.put("corpsCode", corpsCode);
        result.put("corpsLibelle", corpsLibelle);
        return result;
    }

    private String construireDescriptionAudit(EtatReclassement etat, LocalDate effet) {
        Map<String, Object> diplome = etat.diplome();
        return "Reclassement de l'agent " + etat.employe().get("matricule")
                + " selon le diplôme " + diplome.get("intitule")
                + " (diplôme agent #" + diplome.get("employe_diplome_id")
                + ", niveau #" + diplome.get("niveau_id") + " "
                + diplome.get("niveau")
                + ", ordre académique " + diplome.get("ordre_academique")
                + ", obtenu le " + diplome.get("date_obtention") + "). "
                + "Résultat RECLASSIFIED. Catégorie/corps : "
                + "catégorie #" + etat.categorieActuelleId() + " "
                + valeurAudit(etat.employe().get("categorie"))
                + " (corps #" + etat.corpsActuelId() + ") "
                + " / " + valeurAudit(etat.employe().get("corps_libelle"))
                + " vers catégorie #" + etat.categorieCibleId() + " "
                + valeurAudit(diplome.get("categorie_code"))
                + " (corps #" + etat.corpsCibleId() + ") "
                + " / " + valeurAudit(diplome.get("corps_libelle"))
                + "; classe cible " + valeurAudit(etat.regle().get("classe"))
                + ", échelon cible " + etat.echelonCibleId()
                + ", référence " + valeurAudit(etat.regle().get("reference_juridique"))
                + "; effet au " + effet + ".";
    }

    private String valeurAudit(Object value) {
        return value == null ? "non renseigné" : value.toString();
    }

    private void verifierSpers(Integer acteurId) {
        if (acteurId == null || acteurId <= 0
                || !utilisateurRepository.estAgentSpersActif(acteurId)) {
            throw erreur("FORBIDDEN", HttpStatus.FORBIDDEN,
                    "Seul un agent actif autorisé du Service du Personnel peut déclencher un reclassement.");
        }
    }

    private void verifierChef(Integer acteurId) {
        if (acteurId == null || acteurId <= 0
                || !utilisateurRepository.estChefActif(acteurId)) {
            throw erreur("FORBIDDEN", HttpStatus.FORBIDDEN,
                    "Seul un chef actif du Service du Personnel peut configurer les référentiels.");
        }
    }

    private void verifierEmployeId(Integer employeId) {
        if (employeId == null || employeId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'agent est invalide."
            );
        }
    }

    private ReclassementException erreur(
            String code,
            HttpStatus status,
            String message
    ) {
        return new ReclassementException(code, status, message);
    }

    private Integer convertirEnInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? null : Integer.valueOf(value.toString());
    }

    private LocalDate lireDateNullable(Object value) {
        return value == null ? null : convertirDate(value);
    }

    private LocalDate convertirDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.util.Date date) {
            return new java.sql.Date(date.getTime()).toLocalDate();
        }
        if (value instanceof String date) {
            return LocalDate.parse(date);
        }
        throw new IllegalStateException("La date de carrière est invalide.");
    }

    private boolean objetsEgaux(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    private record EtatReclassement(
            Map<String, Object> employe,
            Map<String, Object> diplome,
            Map<String, Object> situation,
            Map<String, Object> regle,
            Integer categorieActuelleId,
            Integer corpsActuelId,
            Integer categorieCibleId,
            Integer corpsCibleId,
            Integer classeCibleId,
            Integer echelonCibleId
    ) {
        private Integer employeId() {
            Object id = employe.get("id");
            return id instanceof Number number ? number.intValue()
                    : Integer.valueOf(id.toString());
        }

        private boolean changementNecessaire() {
            return !Objects.equals(categorieActuelleId, categorieCibleId)
                    || !Objects.equals(corpsActuelId, corpsCibleId);
        }
    }
}

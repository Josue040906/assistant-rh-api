package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.DiplomeRepository;
import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class DiplomeService {

    private final DiplomeRepository diplomeRepository;
    private final EmployeRepository employeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ActiviteService activiteService;

    public DiplomeService(
            DiplomeRepository diplomeRepository,
            EmployeRepository employeRepository,
            UtilisateurRepository utilisateurRepository,
            ActiviteService activiteService
    ) {
        this.diplomeRepository = diplomeRepository;
        this.employeRepository = employeRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.activiteService = activiteService;
    }

    public Map<String, Object> obtenirOptionsFormulaire(
            Integer acteurId,
            Integer niveauId,
            Integer domaineId
    ) {
        verifierActeur(acteurId);
        if (niveauId != null && (niveauId <= 0
                || !diplomeRepository.niveauExiste(niveauId))) {
            throw new IllegalArgumentException("Le niveau de diplôme est invalide.");
        }
        if (domaineId != null && (niveauId == null || domaineId <= 0
                || !diplomeRepository.domaineDisponible(niveauId, domaineId))) {
            throw new IllegalArgumentException(
                    "Le domaine n'est pas proposé pour le niveau sélectionné."
            );
        }

        return Map.of(
                "niveaux", diplomeRepository.findNiveauxFormulaire(),
                "domaines", diplomeRepository.findDomainesFormulaire(niveauId),
                "etablissements", niveauId == null || domaineId == null
                        ? List.of()
                        : diplomeRepository.findEtablissementsFormulaire(
                                niveauId, domaineId
                        ),
                "mentions", diplomeRepository.findMentionsFormulaire()
        );
    }

    public List<Map<String, Object>> obtenirDiplomes(
            Integer employeId,
            Integer acteurId
    ) {
        verifierAgent(employeId);
        verifierActeur(acteurId);
        return diplomeRepository.findByEmployeId(employeId);
    }

    @Transactional
    public void ajouterDiplome(
            Integer employeId,
            Integer acteurId,
            Integer niveauId,
            Integer domaineId,
            Integer etablissementId,
            LocalDate dateObtention,
            Integer mentionId,
            String observation
    ) {
        verifierAgent(employeId);
        verifierActeur(acteurId);
        if (!diplomeRepository.verrouillerEmploye(employeId)) {
            throw new IllegalArgumentException("L'agent demandé n'existe pas.");
        }
        if (niveauId == null || niveauId <= 0) {
            throw new IllegalArgumentException("Le niveau de diplôme est obligatoire.");
        }
        if (domaineId == null || domaineId <= 0) {
            throw new IllegalArgumentException("Le domaine d'études est obligatoire.");
        }
        if (etablissementId == null || etablissementId <= 0) {
            throw new IllegalArgumentException("L'établissement est obligatoire.");
        }
        if (dateObtention == null || dateObtention.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "La date d'obtention est obligatoire et ne peut pas être future."
            );
        }
        String observationNormalisee = validerTexte(
                observation, "L'observation", 2000, false
        );
        Map<String, Object> offre = diplomeRepository.findOffreFormulaire(
                        niveauId, domaineId, etablissementId
                )
                .orElseThrow(() -> new IllegalArgumentException(
                        "L'établissement ne propose pas ce niveau dans le domaine sélectionné."
                ));
        if (mentionId != null
                && diplomeRepository.findMentionFormulaire(mentionId).isEmpty()) {
            throw new IllegalArgumentException(
                    "La mention sélectionnée n'existe pas ou n'est plus active."
            );
        }
        Map<String, Object> agent = employeRepository.findById(employeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "L'agent demandé n'existe pas."
                ));

        try {
            int inserted = diplomeRepository.ajouter(
                    employeId,
                    niveauId,
                    domaineId,
                    etablissementId,
                    java.sql.Date.valueOf(dateObtention),
                    mentionId,
                    observationNormalisee
            );
            if (inserted != 1) {
                throw new IllegalStateException(
                        "Le diplôme n'a pas pu être enregistré."
                );
            }
        } catch (DuplicateKeyException exception) {
            throw new DiplomeDejaEnregistreException();
        }

        activiteService.enregistrer(
                acteurId,
                employeId,
                "CREATION",
                "Ajout du diplôme "
                        + offre.get("niveau")
                        + " en "
                        + offre.get("domaine")
                        + " à "
                        + offre.get("etablissement")
                        + " pour l'agent "
                        + agent.get("matricule")
        );
    }

    @Transactional
    public void demanderAjoutEtablissement(
            Integer acteurId,
            String nom,
            String ville
    ) {
        verifierActeur(acteurId);
        String nomNormalise = validerTexte(nom, "Le nom de l'établissement", 255, true);
        String villeNormalisee = validerTexte(ville, "La ville", 255, false);
        if (diplomeRepository.ajouterDemandeEtablissement(
                nomNormalise, villeNormalisee
        ) != 1) {
            throw new IllegalStateException(
                    "Un établissement portant ce nom existe déjà ou est déjà en attente de validation."
            );
        }
    }

    private void verifierActeur(Integer acteurId) {
        if (acteurId == null || acteurId <= 0
                || !utilisateurRepository.estAgentSpersActif(acteurId)) {
            throw new IllegalArgumentException(
                    "L'acteur n'est pas un utilisateur actif autorisé du Service du Personnel."
            );
        }
    }

    private void verifierAgent(Integer employeId) {
        if (employeId == null || employeId <= 0) {
            throw new IllegalArgumentException("L'identifiant de l'agent est invalide.");
        }
        if (employeRepository.findById(employeId).isEmpty()) {
            throw new IllegalArgumentException("L'agent demandé n'existe pas.");
        }
    }

    private String validerTexte(
            String value,
            String label,
            int maximum,
            boolean required
    ) {
        String normalized = value == null || value.isBlank()
                ? null
                : value.trim();
        if (required && normalized == null) {
            throw new IllegalArgumentException(label + " est obligatoire.");
        }
        if (normalized != null && normalized.length() > maximum) {
            throw new IllegalArgumentException(
                    label + " ne peut pas dépasser " + maximum + " caractères."
            );
        }
        return normalized;
    }
}

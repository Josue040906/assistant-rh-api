package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.RegleRhRepository;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RegleRhService {

    private final RegleRhRepository regleRhRepository;

    public RegleRhService(RegleRhRepository regleRhRepository) {
        this.regleRhRepository = regleRhRepository;
    }

    // =========================================================
    // CRUD ADMINISTRATION
    // =========================================================

    public List<Map<String, Object>> getAllRegles() {
        return regleRhRepository.findAll();
    }

    public Optional<Map<String, Object>> getRegleById(Integer id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }

        Map<String, Object> regle = regleRhRepository.findById(id);

        if (regle == null || regle.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(regle);
    }
    public List<Map<String, Object>> rechercherRegles(String query) {
        if (query == null || query.isBlank()) {
            return getAllRegles();
        }

        return regleRhRepository.search(query.trim());
    }

    public List<Map<String, Object>> getTypesRegles() {
        return regleRhRepository.findTypesRegles();
    }

    public Map<String, Object> createRegle(
            Integer typeRegleId,
            String code,
            String libelle,
            String description,
            String populationConcernee,
            String referenceJuridique,
            String article,
            LocalDate dateDebutValidite,
            LocalDate dateFinValidite,
            Integer priorite,
            Boolean active
    ) {
        validateRegle(
                typeRegleId,
                code,
                libelle,
                populationConcernee,
                article,
                dateDebutValidite,
                dateFinValidite,
                priorite
        );

        Boolean activeValue = active != null ? active : true;

        return regleRhRepository.create(
                typeRegleId,
                code.trim(),
                libelle.trim(),
                nettoyer(description),
                nettoyer(populationConcernee),
                nettoyer(referenceJuridique),
                nettoyer(article),
                convertirDate(dateDebutValidite),
                convertirDate(dateFinValidite),
                priorite,
                activeValue
        );
    }

    public Optional<Map<String, Object>> updateRegle(
            Integer id,
            Integer typeRegleId,
            String code,
            String libelle,
            String description,
            String populationConcernee,
            String referenceJuridique,
            String article,
            LocalDate dateDebutValidite,
            LocalDate dateFinValidite,
            Integer priorite,
            Boolean active
    ) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }

        Map<String, Object> regleExistante =
                regleRhRepository.findById(id);

        if (regleExistante == null || regleExistante.isEmpty()) {
            return Optional.empty();
        }

        validateRegle(
                typeRegleId,
                code,
                libelle,
                populationConcernee,
                article,
                dateDebutValidite,
                dateFinValidite,
                priorite
        );

        Boolean activeValue = active != null ? active : true;

        return Optional.of(
                regleRhRepository.update(
                        id,
                        typeRegleId,
                        code.trim(),
                        libelle.trim(),
                        nettoyer(description),
                        nettoyer(populationConcernee),
                        nettoyer(referenceJuridique),
                        nettoyer(article),
                        convertirDate(dateDebutValidite),
                        convertirDate(dateFinValidite),
                        priorite,
                        activeValue
                )
        );
    }

    public boolean deleteRegle(Integer id) {
        if (id == null || id <= 0) {
            return false;
        }

        Map<String, Object> regleExistante =
                regleRhRepository.findById(id);

        if (regleExistante == null || regleExistante.isEmpty()) {
            return false;
        }

        return regleRhRepository.delete(id);
    }

    // =========================================================
    // FONCTIONS MÉTIER EXISTANTES
    // =========================================================

    public Optional<Map<String, Object>> obtenirRegleComplete(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }

        Optional<Map<String, Object>> regle =
                regleRhRepository.findRegleActiveByCode(code.trim());

        if (regle.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> resultat = regle.get();

        Integer regleId =
                ((Number) resultat.get("id")).intValue();

        resultat.put(
                "population",
                regleRhRepository.findPopulationByRegleId(regleId)
        );

        resultat.put(
                "conditions",
                regleRhRepository.findConditionsByRegleId(regleId)
        );

        resultat.put(
                "effets",
                regleRhRepository.findEffetsByRegleId(regleId)
        );

        resultat.put(
                "references_juridiques",
                regleRhRepository.findReferencesJuridiquesByRegleId(regleId)
        );

        return Optional.of(resultat);
    }

    public boolean estApplicable(
            Map<String, Object> regle,
            Map<String, Object> situation
    ) {
        if (regle == null || situation == null) {
            return false;
        }

        Object populationObj = regle.get("population");

        if (!(populationObj instanceof List<?> populations)
                || populations.isEmpty()) {
            return false;
        }

        for (Object populationObjItem : populations) {

            if (!(populationObjItem instanceof Map<?, ?> population)) {
                continue;
            }

            if (correspond(
                    population.get("statut_agent"),
                    situation.get("statut_agent_code")
            )
                    && correspond(
                    population.get("corps"),
                    situation.get("corps_code")
            )) {
                return true;
            }
        }

        return false;
    }

    private boolean correspond(
            Object valeurRegle,
            Object valeurSituation
    ) {
        if (valeurRegle == null) {
            return true;
        }

        if (valeurSituation == null) {
            return false;
        }

        return valeurRegle
                .toString()
                .equalsIgnoreCase(valeurSituation.toString());
    }

    public List<Map<String, Object>> obtenirReglesActives() {

        List<Map<String, Object>> regles =
                regleRhRepository.findReglesActives();

        for (Map<String, Object> regle : regles) {

            Integer regleId =
                    ((Number) regle.get("id")).intValue();

            regle.put(
                    "population",
                    regleRhRepository.findPopulationByRegleId(regleId)
            );

            regle.put(
                    "conditions",
                    regleRhRepository.findConditionsByRegleId(regleId)
            );

            regle.put(
                    "effets",
                    regleRhRepository.findEffetsByRegleId(regleId)
            );

            regle.put(
                    "references_juridiques",
                    regleRhRepository.findReferencesJuridiquesByRegleId(regleId)
            );
        }

        return regles;
    }

    // =========================================================
    // VALIDATION
    // =========================================================

    private void validateRegle(
            Integer typeRegleId,
            String code,
            String libelle,
            String populationConcernee,
            String article,
            LocalDate dateDebutValidite,
            LocalDate dateFinValidite,
            Integer priorite
    ) {

        if (typeRegleId == null || typeRegleId <= 0) {
            throw new IllegalArgumentException(
                    "Le type de règle est obligatoire."
            );
        }

        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(
                    "Le code de la règle est obligatoire."
            );
        }

        if (code.trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Le code ne doit pas dépasser 100 caractères."
            );
        }

        if (libelle == null || libelle.isBlank()) {
            throw new IllegalArgumentException(
                    "Le libellé de la règle est obligatoire."
            );
        }

        if (libelle.trim().length() > 255) {
            throw new IllegalArgumentException(
                    "Le libellé ne doit pas dépasser 255 caractères."
            );
        }

        if (populationConcernee != null
                && populationConcernee.length() > 255) {
            throw new IllegalArgumentException(
                    "La population concernée ne doit pas dépasser 255 caractères."
            );
        }

        if (article != null && article.length() > 100) {
            throw new IllegalArgumentException(
                    "L'article ne doit pas dépasser 100 caractères."
            );
        }

        if (dateDebutValidite != null
                && dateFinValidite != null
                && dateFinValidite.isBefore(dateDebutValidite)) {

            throw new IllegalArgumentException(
                    "La date de fin de validité ne peut pas être antérieure à la date de début."
            );
        }

        if (priorite != null && priorite < 0) {
            throw new IllegalArgumentException(
                    "La priorité doit être supérieure ou égale à 0."
            );
        }
    }

    private Date convertirDate(LocalDate date) {
        return date != null ? Date.valueOf(date) : null;
    }

    private String nettoyer(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }

        return valeur.trim();
    }
}
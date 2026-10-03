package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.GradeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class GradeService {

    private final GradeRepository gradeRepository;

    public GradeService(GradeRepository gradeRepository) {
        this.gradeRepository = gradeRepository;
    }

    public List<Map<String, Object>> getAllGrades() {
        return gradeRepository.findAll();
    }

    public Map<String, Object> getGradeById(Long id) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant du grade est invalide."
            );
        }

        return gradeRepository.findById(id);
    }

    public List<Map<String, Object>> rechercherGrades(String query) {

        if (query == null || query.trim().isEmpty()) {
            return getAllGrades();
        }

        return gradeRepository.search(query);
    }

    public Map<String, Object> createGrade(
            String code,
            String libelle,
            String description,
            String referenceJuridique,
            LocalDate dateDebutValidite,
            LocalDate dateFinValidite
    ) {

        validerGrade(
                code,
                libelle,
                dateDebutValidite,
                dateFinValidite
        );

        return gradeRepository.create(
                code.trim(),
                libelle.trim(),
                nettoyer(description),
                nettoyer(referenceJuridique),
                dateDebutValidite,
                dateFinValidite
        );
    }

    public Map<String, Object> updateGrade(
            Long id,
            String code,
            String libelle,
            String description,
            String referenceJuridique,
            LocalDate dateDebutValidite,
            LocalDate dateFinValidite
    ) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant du grade est invalide."
            );
        }

        validerGrade(
                code,
                libelle,
                dateDebutValidite,
                dateFinValidite
        );

        return gradeRepository.update(
                id,
                code.trim(),
                libelle.trim(),
                nettoyer(description),
                nettoyer(referenceJuridique),
                dateDebutValidite,
                dateFinValidite
        );
    }

    public boolean deleteGrade(Long id) {

        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant du grade est invalide."
            );
        }

        return gradeRepository.delete(id);
    }

    private void validerGrade(
            String code,
            String libelle,
            LocalDate dateDebutValidite,
            LocalDate dateFinValidite
    ) {

        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Le code du grade est obligatoire."
            );
        }

        if (code.trim().length() > 50) {
            throw new IllegalArgumentException(
                    "Le code du grade ne doit pas dépasser 50 caractères."
            );
        }

        if (libelle == null || libelle.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Le libellé du grade est obligatoire."
            );
        }

        if (libelle.trim().length() > 200) {
            throw new IllegalArgumentException(
                    "Le libellé du grade ne doit pas dépasser 200 caractères."
            );
        }

        if (dateDebutValidite != null
                && dateFinValidite != null
                && dateFinValidite.isBefore(dateDebutValidite)) {

            throw new IllegalArgumentException(
                    "La date de fin de validité ne peut pas être antérieure à la date de début."
            );
        }
    }

    private String nettoyer(String valeur) {

        if (valeur == null) {
            return null;
        }

        String resultat = valeur.trim();

        return resultat.isEmpty() ? null : resultat;
    }
}
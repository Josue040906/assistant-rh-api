        package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.GradeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class GradeService {

    private final GradeRepository gradeRepository;

    public GradeService(GradeRepository gradeRepository) {
        this.gradeRepository = gradeRepository;
    }

    /**
     * Récupère tous les grades.
     */
    public List<Map<String, Object>> getAllGrades() {
        return gradeRepository.findAll();
    }

    /**
     * Récupère un grade par son identifiant.
     */
    public Map<String, Object> getGradeById(Long id) {
        return gradeRepository.findById(id);
    }

    /**
     * Recherche des grades.
     */
    public List<Map<String, Object>> rechercherGrades(String query) {

        if (query == null || query.trim().isEmpty()) {
            return getAllGrades();
        }

        return gradeRepository.search(query);
    }

    /**
     * Crée un grade.
     */
    public Map<String, Object> createGrade(
            String codeGrade,
            Long typeEmploiId
    ) {

        validerGrade(codeGrade, typeEmploiId);

        return gradeRepository.create(
                codeGrade.trim(),
                typeEmploiId
        );
    }

    /**
     * Modifie un grade.
     */
    public Map<String, Object> updateGrade(
            Long id,
            String codeGrade,
            Long typeEmploiId
    ) {

        validerGrade(codeGrade, typeEmploiId);

        return gradeRepository.update(
                id,
                codeGrade.trim(),
                typeEmploiId
        );
    }

    /**
     * Supprime un grade.
     */
    public boolean deleteGrade(Long id) {
        return gradeRepository.delete(id);
    }

    /**
     * Validation commune aux créations et modifications.
     */
    private void validerGrade(
            String codeGrade,
            Long typeEmploiId
    ) {

        if (codeGrade == null ||
                codeGrade.trim().isEmpty()) {

            throw new IllegalArgumentException(
                    "Le code du grade est obligatoire."
            );
        }

        if (codeGrade.trim().length() > 100) {

            throw new IllegalArgumentException(
                    "Le code du grade ne doit pas dépasser 100 caractères."
            );
        }

        if (typeEmploiId == null) {

            throw new IllegalArgumentException(
                    "Le type d'emploi est obligatoire."
            );
        }

        if (typeEmploiId <= 0) {

            throw new IllegalArgumentException(
                    "Le type d'emploi sélectionné est invalide."
            );
        }
    }
}

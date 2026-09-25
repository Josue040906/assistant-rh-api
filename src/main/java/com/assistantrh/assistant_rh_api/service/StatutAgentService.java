        package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.StatutAgentRepository;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.util.List;
import java.util.Map;

@Service
public class StatutAgentService {

    private final StatutAgentRepository statutAgentRepository;

    public StatutAgentService(
            StatutAgentRepository statutAgentRepository) {
        this.statutAgentRepository = statutAgentRepository;
    }

    public List<Map<String, Object>> getAllStatuts() {
        return statutAgentRepository.findAll();
    }

    public Map<String, Object> getStatutById(Long id) {
        return statutAgentRepository.findById(id);
    }

    public List<Map<String, Object>> rechercherStatuts(String query) {
        if (query == null || query.trim().isEmpty()) {
            return getAllStatuts();
        }

        return statutAgentRepository.search(query.trim());
    }

    public Map<String, Object> createStatut(
            String code,
            String libelle,
            String description,
            Date dateDebutValidite,
            Date dateFinValidite
    ) {
        validerStatut(
                code,
                libelle,
                dateDebutValidite,
                dateFinValidite
        );

        return statutAgentRepository.create(
                code.trim(),
                libelle.trim(),
                description != null ? description.trim() : null,
                dateDebutValidite,
                dateFinValidite
        );
    }

    public Map<String, Object> updateStatut(
            Long id,
            String code,
            String libelle,
            String description,
            Date dateDebutValidite,
            Date dateFinValidite
    ) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant du statut est invalide."
            );
        }

        validerStatut(
                code,
                libelle,
                dateDebutValidite,
                dateFinValidite
        );

        return statutAgentRepository.update(
                id,
                code.trim(),
                libelle.trim(),
                description != null ? description.trim() : null,
                dateDebutValidite,
                dateFinValidite
        );
    }

    public boolean deleteStatut(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant du statut est invalide."
            );
        }

        return statutAgentRepository.delete(id);
    }

    private void validerStatut(
            String code,
            String libelle,
            Date dateDebutValidite,
            Date dateFinValidite
    ) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Le code du statut est obligatoire."
            );
        }

        if (code.trim().length() > 100) {
            throw new IllegalArgumentException(
                    "Le code du statut ne doit pas dépasser 100 caractères."
            );
        }

        if (libelle == null || libelle.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Le libellé du statut est obligatoire."
            );
        }

        if (libelle.trim().length() > 255) {
            throw new IllegalArgumentException(
                    "Le libellé du statut ne doit pas dépasser 255 caractères."
            );
        }

        if (dateDebutValidite == null) {
            throw new IllegalArgumentException(
                    "La date de début de validité est obligatoire."
            );
        }

        if (dateFinValidite != null
                && dateFinValidite.before(dateDebutValidite)) {
            throw new IllegalArgumentException(
                    "La date de fin de validité ne peut pas être antérieure à la date de début."
            );
        }
    }
}

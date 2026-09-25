        package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.PosteRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PosteService {

    private final PosteRepository posteRepository;

    public PosteService(PosteRepository posteRepository) {
        this.posteRepository = posteRepository;
    }

    public List<Map<String, Object>> getAllPostes() {
        return posteRepository.findAll();
    }

    public Map<String, Object> getPosteById(Long id) {
        return posteRepository.findById(id);
    }

    public List<Map<String, Object>> rechercherPostes(String query) {

        if (query == null || query.isBlank()) {
            return List.of();
        }

        return posteRepository.search(query.trim());
    }

    public List<Map<String, Object>> rechercherPostesParService(
            Long serviceId
    ) {

        if (serviceId == null) {
            return List.of();
        }

        return posteRepository.findByService(serviceId);
    }


    public Map<String, Object> createPoste(
            String intitule,
            String description,
            Long serviceId
    ) {
        validateIntitule(intitule);
        validateServiceId(serviceId);

        return posteRepository.create(
                intitule.trim(),
                normalizeDescription(description),
                serviceId
        );
    }

    public Map<String, Object> updatePoste(
            Long id,
            String intitule,
            String description,
            Long serviceId
    ) {
        validateIntitule(intitule);
        validateServiceId(serviceId);

        Map<String, Object> existing =
                posteRepository.findById(id);

        if (existing == null) {
            return null;
        }

        return posteRepository.update(
                id,
                intitule.trim(),
                normalizeDescription(description),
                serviceId
        );
    }

    public boolean deletePoste(Long id) {
        Map<String, Object> existing =
                posteRepository.findById(id);

        if (existing == null) {
            return false;
        }

        return posteRepository.delete(id);
    }

    private void validateIntitule(String intitule) {
        if (intitule == null || intitule.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "L'intitulé du poste est obligatoire."
            );
        }

        if (intitule.trim().length() > 200) {
            throw new IllegalArgumentException(
                    "L'intitulé du poste ne doit pas dépasser 200 caractères."
            );
        }
    }

    private void validateServiceId(Long serviceId) {
        if (serviceId == null) {
            throw new IllegalArgumentException(
                    "Le service du poste est obligatoire."
            );
        }
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }

        String value = description.trim();

        return value.isEmpty() ? null : value;
    }
}

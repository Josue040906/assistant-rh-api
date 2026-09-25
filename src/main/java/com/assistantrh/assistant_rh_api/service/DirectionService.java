package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.DirectionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DirectionService {

    private final DirectionRepository directionRepository;

    public DirectionService(DirectionRepository directionRepository) {
        this.directionRepository = directionRepository;
    }

    public List<Map<String, Object>> getAllDirections() {
        return directionRepository.findAll();
    }

    public Map<String, Object> getDirectionById(Long id) {
        return directionRepository.findById(id);
    }

    public Map<String, Object> createDirection(
            String nom,
            String description
    ) {
        validateNom(nom);

        return directionRepository.create(
                nom.trim(),
                normalizeDescription(description)
        );
    }

    public Map<String, Object> updateDirection(
            Long id,
            String nom,
            String description
    ) {
        validateNom(nom);

        Map<String, Object> existing =
                directionRepository.findById(id);

        if (existing == null) {
            return null;
        }

        return directionRepository.update(
                id,
                nom.trim(),
                normalizeDescription(description)
        );
    }

    public boolean deleteDirection(Long id) {
        Map<String, Object> existing =
                directionRepository.findById(id);

        if (existing == null) {
            return false;
        }

        return directionRepository.delete(id);
    }

    private void validateNom(String nom) {
        if (nom == null || nom.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Le nom de la direction est obligatoire."
            );
        }

        if (nom.trim().length() > 200) {
            throw new IllegalArgumentException(
                    "Le nom de la direction ne doit pas dépasser 200 caractères."
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

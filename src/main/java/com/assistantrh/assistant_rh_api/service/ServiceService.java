        package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.ServiceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ServiceService {

    private final ServiceRepository serviceRepository;

    public ServiceService(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    public List<Map<String, Object>> getAllServices() {
        return serviceRepository.findAll();
    }

    public Map<String, Object> getServiceById(Long id) {
        return serviceRepository.findById(id);
    }

    public List<Map<String, Object>> rechercherServices(String query) {

        if (query == null || query.isBlank()) {
            return List.of();
        }

        return serviceRepository.search(query.trim());
    }

    public Map<String, Object> createService(
            String code,
            String nom,
            String description,
            Long directionId
    ) {
        validateCode(code);
        validateNom(nom);

        return serviceRepository.create(
                code.trim(),
                nom.trim(),
                normalizeDescription(description),
                directionId
        );
    }

    public Map<String, Object> updateService(
            Long id,
            String code,
            String nom,
            String description,
            Long directionId
    ) {
        validateCode(code);
        validateNom(nom);

        Map<String, Object> existing =
                serviceRepository.findById(id);

        if (existing == null) {
            return null;
        }

        return serviceRepository.update(
                id,
                code.trim(),
                nom.trim(),
                normalizeDescription(description),
                directionId
        );
    }

    public boolean deleteService(Long id) {
        Map<String, Object> existing =
                serviceRepository.findById(id);

        if (existing == null) {
            return false;
        }

        return serviceRepository.delete(id);
    }

    private void validateCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Le code du service est obligatoire."
            );
        }

        if (code.trim().length() > 50) {
            throw new IllegalArgumentException(
                    "Le code du service ne doit pas dépasser 50 caractères."
            );
        }
    }

    private void validateNom(String nom) {
        if (nom == null || nom.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Le nom du service est obligatoire."
            );
        }

        if (nom.trim().length() > 200) {
            throw new IllegalArgumentException(
                    "Le nom du service ne doit pas dépasser 200 caractères."
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

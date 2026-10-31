package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.OrganisationRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OrganisationService {

    private final OrganisationRepository repository;

    public OrganisationService(OrganisationRepository repository) {
        this.repository = repository;
    }

    public Object executer(
            String operation,
            Long id,
            Long directionId,
            Long serviceId,
            String query
    ) {
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("L'opération est obligatoire.");
        }

        return switch (operation.trim().toUpperCase(Locale.ROOT)) {
            case "LIST_DIRECTIONS" -> repository.listerDirections(query);
            case "GET_DIRECTION" -> exigerId(id, "direction", id == null ? null : repository.obtenirDirection(id));
            case "LIST_SERVICES" -> repository.listerServices(directionId, query);
            case "GET_SERVICE" -> exigerId(id, "service", id == null ? null : repository.obtenirService(id));
            case "LIST_POSTES" -> repository.listerPostes(serviceId, query);
            case "GET_POSTE" -> exigerId(id, "poste", id == null ? null : repository.obtenirPoste(id));
            case "GET_HIERARCHY" -> repository.obtenirHierarchie(directionId);
            case "GET_RESPONSIBLE", "LIST_RESPONSIBLES" ->
                    repository.listerResponsables(directionId, serviceId);
            case "COUNT_DIRECTIONS" -> resultatCompteur("nombre_directions", repository.compterDirections());
            case "COUNT_SERVICES" -> resultatCompteur("nombre_services", repository.compterServices(directionId));
            case "COUNT_POSTES" -> resultatCompteur("nombre_postes", repository.compterPostes(serviceId));
            case "COUNT_EMPLOYEES_BY_STRUCTURE" ->
                    resultatCompteur("nombre_agents", repository.compterEmployes(directionId, serviceId));
            case "GET_EMPLOYEE_SUPERVISION" ->
                    throw new UnsupportedOperationException(
                            "SYGPERS ne contient pas de relation explicite entre un agent et son supérieur hiérarchique."
                    );
            default -> throw new IllegalArgumentException("Opération d'organisation inconnue : " + operation);
        };
    }

    private Object exigerId(Long id, String type, Map<String, Object> resultat) {
        if (id == null) {
            throw new IllegalArgumentException("L'identifiant du " + type + " est obligatoire.");
        }
        if (resultat == null) {
            throw new RessourceOrganisationIntrouvableException(
                    "Aucun " + type + " ne correspond à cet identifiant."
            );
        }
        return resultat;
    }

    private Map<String, Object> resultatCompteur(String cle, long valeur) {
        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put(cle, valeur);
        return resultat;
    }

    public static class RessourceOrganisationIntrouvableException extends RuntimeException {
        public RessourceOrganisationIntrouvableException(String message) {
            super(message);
        }
    }
}

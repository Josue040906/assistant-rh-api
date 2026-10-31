package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.controller.OrganisationController.ConsultationRequest;
import com.assistantrh.assistant_rh_api.repository.OrganisationRepository;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class OrganisationService {
    private static final Set<String> OPERATIONS = Set.of(
            "LIST_DIRECTIONS", "GET_DIRECTION", "LIST_SERVICES", "GET_SERVICE",
            "LIST_POSTES", "GET_POSTE", "GET_HIERARCHY", "GET_RESPONSIBLE",
            "LIST_RESPONSIBLES", "COUNT_DIRECTIONS", "COUNT_SERVICES",
            "COUNT_POSTES", "COUNT_EMPLOYEES_BY_STRUCTURE"
    );

    private final OrganisationRepository repository;

    public OrganisationService(OrganisationRepository repository) {
        this.repository = repository;
    }

    public Map<String,Object> consulter(ConsultationRequest request) {
        if (request == null || request.operation() == null || request.operation().isBlank()) {
            throw new IllegalArgumentException("L'opération de consultation est obligatoire.");
        }
        String op = request.operation().trim().toUpperCase(Locale.ROOT);
        if (!OPERATIONS.contains(op)) {
            throw new IllegalArgumentException("Opération organisationnelle non prise en charge : " + op);
        }

        Object data = switch (op) {
            case "LIST_DIRECTIONS" -> repository.directions(request.query());
            case "GET_DIRECTION" -> getDirection(required(request.directionId(), "directionId"));
            case "LIST_SERVICES" -> repository.services(request.directionId(), request.query());
            case "GET_SERVICE" -> getService(required(request.serviceId(), "serviceId"));
            case "LIST_POSTES" -> repository.postes(
                    request.directionId(), request.serviceId(), request.query(), includeVacant(request));
            case "GET_POSTE" -> getPoste(required(request.posteId(), "posteId"));
            case "GET_HIERARCHY" -> hierarchy(request);
            case "GET_RESPONSIBLE" -> responsible(request);
            case "LIST_RESPONSIBLES" -> repository.responsables(
                    request.directionId(), request.serviceId(), request.query());
            case "COUNT_DIRECTIONS" -> Map.of("nombre", repository.countDirections());
            case "COUNT_SERVICES" -> Map.of("nombre", repository.countServices(request.directionId()));
            case "COUNT_POSTES" -> Map.of("nombre", repository.countPostes(
                    request.directionId(), request.serviceId()));
            case "COUNT_EMPLOYEES_BY_STRUCTURE" -> repository.effectifs(
                    request.directionId(), request.serviceId());
            default -> throw new IllegalStateException("Opération non traitée : " + op);
        };

        Map<String,Object> response = new LinkedHashMap<>();
        response.put("operation", op);
        response.put("data", data);
        if (data instanceof List<?> values) response.put("total", values.size());
        return response;
    }

    private Map<String,Object> getDirection(Long id) {
        Map<String,Object> value = repository.direction(id);
        if (value == null) throw new IllegalArgumentException("Direction introuvable.");
        Map<String,Object> result = new LinkedHashMap<>(value);
        result.put("services", repository.services(id, null));
        return result;
    }

    private Map<String,Object> getService(Long id) {
        Map<String,Object> value = repository.service(id);
        if (value == null) throw new IllegalArgumentException("Service introuvable.");
        Map<String,Object> result = new LinkedHashMap<>(value);
        result.put("postes", repository.postes(null, id, null, true));
        return result;
    }

    private Map<String,Object> getPoste(Long id) {
        Map<String,Object> value = repository.poste(id);
        if (value == null) throw new IllegalArgumentException("Poste introuvable.");
        Map<String,Object> result = new LinkedHashMap<>(value);
        result.put("titulaires", repository.titulaires(id));
        return result;
    }

    private List<Map<String,Object>> responsible(ConsultationRequest request) {
        if (request.directionId() == null && request.serviceId() == null
                && (request.query() == null || request.query().isBlank())) {
            throw new IllegalArgumentException(
                    "Précisez une direction, un service ou un terme de recherche pour identifier un responsable.");
        }
        return repository.responsables(request.directionId(), request.serviceId(), request.query());
    }

    private List<Map<String,Object>> hierarchy(ConsultationRequest request) {
        List<Map<String,Object>> rows = repository.hierarchy(
                request.directionId(), request.serviceId(), includeVacant(request));
        Map<Long,Map<String,Object>> directions = new LinkedHashMap<>();
        Map<Long,Map<String,Object>> services = new HashMap<>();

        for (Map<String,Object> row : rows) {
            Long did = asLong(row.get("direction_id"));
            if (did == null) continue;
            Map<String,Object> direction = directions.computeIfAbsent(did, key -> {
                Map<String,Object> d = new LinkedHashMap<>();
                d.put("id", key);
                d.put("nom", row.get("direction"));
                d.put("description", row.get("description_direction"));
                d.put("services", new ArrayList<Map<String,Object>>());
                return d;
            });
            Long sid = asLong(row.get("service_id"));
            if (sid == null) continue;
            Map<String,Object> service = services.get(sid);
            if (service == null) {
                service = new LinkedHashMap<>();
                service.put("id", sid);
                service.put("code", row.get("code_service"));
                service.put("nom", row.get("service"));
                service.put("description", row.get("description_service"));
                service.put("postes", new ArrayList<Map<String,Object>>());
                services.put(sid, service);
                list(direction, "services").add(service);
            }
            Long pid = asLong(row.get("poste_id"));
            if (pid != null) {
                Map<String,Object> poste = new LinkedHashMap<>();
                poste.put("id", pid);
                poste.put("intitule", row.get("poste"));
                poste.put("description", row.get("description_poste"));
                poste.put("nombre_titulaires_actuels", row.get("nombre_titulaires_actuels"));
                list(service, "postes").add(poste);
            }
        }
        return new ArrayList<>(directions.values());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> list(Map<String,Object> parent, String key) {
        return (List<Map<String,Object>>) parent.get(key);
    }

    private static boolean includeVacant(ConsultationRequest request) {
        return request.includeVacant() == null || request.includeVacant();
    }

    private static Long required(Long id, String name) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(name + " doit être un identifiant positif.");
        }
        return id;
    }

    private static Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }
}

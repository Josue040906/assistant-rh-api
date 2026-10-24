package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.AssistantOrganizationRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AssistantOrganizationService {
    private static final int MAX_RESULTS = 100;
    private static final List<String> SCOPES =
            List.of("ALL", "DIRECTION", "SERVICE", "POSTE");

    private final AssistantOrganizationRepository repository;

    public AssistantOrganizationService(
            AssistantOrganizationRepository repository
    ) {
        this.repository = repository;
    }

    public OrganizationResponse search(String query, String requestedScope) {
        if (query == null || query.trim().length() < 2
                || query.trim().length() > 100) {
            throw new IllegalArgumentException(
                    "La recherche organisationnelle doit contenir entre 2 et 100 caractères."
            );
        }
        String scope = requestedScope == null || requestedScope.isBlank()
                ? "ALL"
                : requestedScope.trim().toUpperCase(Locale.ROOT);
        if (!SCOPES.contains(scope)) {
            throw new IllegalArgumentException(
                    "scope doit être ALL, DIRECTION, SERVICE ou POSTE."
            );
        }

        List<Map<String, Object>> rows = repository.search(
                query.trim(),
                scope,
                MAX_RESULTS + 1
        );
        boolean truncated = rows.size() > MAX_RESULTS;
        Map<Long, DirectionBuilder> directions = new LinkedHashMap<>();
        for (Map<String, Object> row : rows.stream().limit(MAX_RESULTS).toList()) {
            Long directionId = asLong(row.get("direction_id"));
            if (directionId == null) {
                continue;
            }
            DirectionBuilder direction = directions.computeIfAbsent(
                    directionId,
                    id -> new DirectionBuilder(
                            id,
                            text(row.get("direction")),
                            text(row.get("direction_description"))
                    )
            );

            Long serviceId = asLong(row.get("service_id"));
            if (serviceId == null) {
                continue;
            }
            ServiceBuilder service = direction.services.computeIfAbsent(
                    serviceId,
                    id -> new ServiceBuilder(
                            id,
                            text(row.get("service_code")),
                            text(row.get("service")),
                            text(row.get("service_description"))
                    )
            );

            Long posteId = asLong(row.get("poste_id"));
            if (posteId != null) {
                service.postes.putIfAbsent(
                        posteId,
                        new PosteInfo(
                                posteId,
                                text(row.get("poste")),
                                text(row.get("poste_description"))
                        )
                );
            }
        }

        List<DirectionInfo> results = directions.values().stream()
                .map(DirectionBuilder::toInfo)
                .toList();
        return new OrganizationResponse(
                query.trim(),
                scope,
                results,
                truncated
        );
    }

    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    public record OrganizationResponse(
            String query,
            String scope,
            List<DirectionInfo> directions,
            boolean truncated
    ) {
    }

    public record DirectionInfo(
            Long id,
            String nom,
            String description,
            List<ServiceInfo> services
    ) {
    }

    public record ServiceInfo(
            Long id,
            String code,
            String nom,
            String description,
            List<PosteInfo> postes
    ) {
    }

    public record PosteInfo(Long id, String intitule, String description) {
    }

    private static final class DirectionBuilder {
        private final Long id;
        private final String nom;
        private final String description;
        private final Map<Long, ServiceBuilder> services = new LinkedHashMap<>();

        private DirectionBuilder(Long id, String nom, String description) {
            this.id = id;
            this.nom = nom;
            this.description = description;
        }

        private DirectionInfo toInfo() {
            return new DirectionInfo(
                    id,
                    nom,
                    description,
                    new ArrayList<>(services.values().stream()
                            .map(ServiceBuilder::toInfo)
                            .toList())
            );
        }
    }

    private static final class ServiceBuilder {
        private final Long id;
        private final String code;
        private final String nom;
        private final String description;
        private final Map<Long, PosteInfo> postes = new LinkedHashMap<>();

        private ServiceBuilder(
                Long id,
                String code,
                String nom,
                String description
        ) {
            this.id = id;
            this.code = code;
            this.nom = nom;
            this.description = description;
        }

        private ServiceInfo toInfo() {
            return new ServiceInfo(
                    id,
                    code,
                    nom,
                    description,
                    List.copyOf(postes.values())
            );
        }
    }
}

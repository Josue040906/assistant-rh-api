package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.AssistantInsightsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class AssistantInsightsService {

    private final AssistantInsightsRepository insightsRepository;
    private final UtilisateurService utilisateurService;

    public AssistantInsightsService(
            AssistantInsightsRepository insightsRepository,
            UtilisateurService utilisateurService
    ) {
        this.insightsRepository = insightsRepository;
        this.utilisateurService = utilisateurService;
    }

    public Map<String, Object> organizationOverview(Integer userId) {
        requireActiveSpersAgent(userId);
        return insightsRepository.organizationOverview();
    }

    public List<Map<String, Object>> employeesByPosition(
            Integer userId,
            String query
    ) {
        requireActiveSpersAgent(userId);
        return insightsRepository.employeesByPosition(
                query == null ? "" : query.trim()
        );
    }

    public List<Map<String, Object>> highestDegreeEmployees(Integer userId) {
        requireActiveSpersAgent(userId);
        return insightsRepository.highestDegreeEmployees();
    }

    public List<Map<String, Object>> recentAssignments(
            Integer userId,
            int limit
    ) {
        requireActiveSpersAgent(userId);
        if (limit < 1 || limit > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La limite doit être comprise entre 1 et 50."
            );
        }
        return insightsRepository.recentAssignments(limit);
    }

    private void requireActiveSpersAgent(Integer userId) {
        if (userId == null || userId <= 0
                || !utilisateurService.estAgentSpersActif(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Un compte agent SPERS actif est requis."
            );
        }
    }
}

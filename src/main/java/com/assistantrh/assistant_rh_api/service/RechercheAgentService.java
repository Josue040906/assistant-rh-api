package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RechercheAgentService {

    private final EmployeRepository employeRepository;

    public RechercheAgentService(EmployeRepository employeRepository) {
        this.employeRepository = employeRepository;
    }

    public List<Map<String, Object>> rechercherAgents(String requete) {

        if (requete == null || requete.isBlank()) {
            return List.of();
        }

        return employeRepository.search(requete.trim());
    }
}
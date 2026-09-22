package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class ProfilAgentService {

    private final EmployeRepository employeRepository;

    public ProfilAgentService(EmployeRepository employeRepository) {
        this.employeRepository = employeRepository;
    }

    public Optional<Map<String, Object>> obtenirProfilAgent(String requete) {

        if (requete == null || requete.isBlank()) {
            return Optional.empty();
        }

        return employeRepository.findProfile(requete.trim());
    }
}
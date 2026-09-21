package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EmployeService {

    private final EmployeRepository employeRepository;

    public EmployeService(EmployeRepository employeRepository) {
        this.employeRepository = employeRepository;
    }

    public List<Map<String, Object>> getAllEmployes() {
        return employeRepository.findAll();
    }

    public Optional<Map<String, Object>> getEmployeById(Integer id) {
        return employeRepository.findById(id);
    }

    public Optional<Map<String, Object>> rechercherProfilEmploye(String query) {
        return employeRepository.findProfile(query);
    }

    public List<Map<String, Object>> rechercherEmployes(String query) {
        return employeRepository.search(query);
    }


}
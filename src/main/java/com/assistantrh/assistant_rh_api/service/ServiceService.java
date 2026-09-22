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

    public List<Map<String, Object>> rechercherServices(String query) {

        if (query == null || query.isBlank()) {
            return List.of();
        }

        return serviceRepository.search(query.trim());
    }
}
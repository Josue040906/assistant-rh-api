package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.PosteRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PosteService {

    private final PosteRepository posteRepository;

    public PosteService(PosteRepository posteRepository) {
        this.posteRepository = posteRepository;
    }

    public List<Map<String, Object>> getAllPostes() {
        return posteRepository.findAll();
    }

    public List<Map<String, Object>> rechercherPostes(String query) {

        if (query == null || query.isBlank()) {
            return List.of();
        }

        return posteRepository.search(query.trim());
    }

    public List<Map<String, Object>> rechercherPostesParService(
            String serviceQuery
    ) {

        if (serviceQuery == null || serviceQuery.isBlank()) {
            return List.of();
        }

        return posteRepository.findByService(serviceQuery.trim());
    }
}
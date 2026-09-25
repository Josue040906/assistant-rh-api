        package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.StatutAgentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class StatutAgentService {

    private final StatutAgentRepository statutAgentRepository;

    public StatutAgentService(
            StatutAgentRepository statutAgentRepository) {
        this.statutAgentRepository = statutAgentRepository;
    }

    public List<Map<String, Object>> getAllStatuts() {
        return statutAgentRepository.findAll();
    }

    public Map<String, Object> getStatutById(Long id) {
        return statutAgentRepository.findById(id);
    }
}

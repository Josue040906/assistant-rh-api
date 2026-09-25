        package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.GradeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class GradeService {

    private final GradeRepository gradeRepository;

    public GradeService(GradeRepository gradeRepository) {
        this.gradeRepository = gradeRepository;
    }

    public List<Map<String, Object>> getAllGrades() {
        return gradeRepository.findAll();
    }

    public Map<String, Object> getGradeById(Long id) {
        return gradeRepository.findById(id);
    }
}


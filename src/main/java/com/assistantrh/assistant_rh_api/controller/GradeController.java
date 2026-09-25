        package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.GradeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/grades")
@CrossOrigin
public class GradeController {

    private final GradeService gradeService;

    public GradeController(GradeService gradeService) {
        this.gradeService = gradeService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllGrades() {
        List<Map<String, Object>> grades =
                gradeService.getAllGrades();

        Map<String, Object> response = new HashMap<>();
        response.put("value", grades);
        response.put("Count", grades.size());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getGradeById(@PathVariable Long id) {
        Map<String, Object> grade =
                gradeService.getGradeById(id);

        if (grade == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(grade);
    }
}

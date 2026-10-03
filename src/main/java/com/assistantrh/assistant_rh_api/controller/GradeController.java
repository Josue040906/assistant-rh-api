package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.GradeService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
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
    public ResponseEntity<?> getAllGrades() {

        List<Map<String, Object>> grades =
                gradeService.getAllGrades();

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put("value", grades);
        response.put("Count", grades.size());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getGradeById(
            @PathVariable Long id
    ) {

        try {

            Map<String, Object> grade =
                    gradeService.getGradeById(id);

            if (grade == null) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "message",
                                "Grade introuvable."
                        ));
            }

            return ResponseEntity.ok(grade);

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "message",
                            e.getMessage()
                    ));
        }
    }

    @GetMapping("/recherche")
    public ResponseEntity<?> rechercherGrades(
            @RequestParam String query
    ) {

        List<Map<String, Object>> grades =
                gradeService.rechercherGrades(query);

        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put("value", grades);
        response.put("Count", grades.size());

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> createGrade(
            @RequestBody GradeRequest request
    ) {

        try {

            Map<String, Object> grade =
                    gradeService.createGrade(
                            request.code(),
                            request.libelle(),
                            request.description(),
                            request.referenceJuridique(),
                            request.dateDebutValidite(),
                            request.dateFinValidite()
                    );

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(grade);

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "message",
                            e.getMessage()
                    ));

        } catch (DataIntegrityViolationException e) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Impossible de créer ce grade. Vérifiez que le code n'est pas déjà utilisé et que les données sont valides."
                    ));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateGrade(
            @PathVariable Long id,
            @RequestBody GradeRequest request
    ) {

        try {

            Map<String, Object> grade =
                    gradeService.updateGrade(
                            id,
                            request.code(),
                            request.libelle(),
                            request.description(),
                            request.referenceJuridique(),
                            request.dateDebutValidite(),
                            request.dateFinValidite()
                    );

            if (grade == null) {

                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "message",
                                "Grade introuvable."
                        ));
            }

            return ResponseEntity.ok(grade);

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "message",
                            e.getMessage()
                    ));

        } catch (DataIntegrityViolationException e) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Impossible de modifier ce grade. Vérifiez que le code n'est pas déjà utilisé et que les données sont valides."
                    ));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteGrade(
            @PathVariable Long id
    ) {

        try {

            boolean deleted =
                    gradeService.deleteGrade(id);

            if (!deleted) {

                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "message",
                                "Grade introuvable."
                        ));
            }

            return ResponseEntity
                    .noContent()
                    .build();

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "message",
                            e.getMessage()
                    ));

        } catch (DataIntegrityViolationException e) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Ce grade ne peut pas être supprimé car il est encore utilisé par des agents ou d'autres données RH."
                    ));
        }
    }

    public record GradeRequest(
            String code,
            String libelle,
            String description,
            String referenceJuridique,
            LocalDate dateDebutValidite,
            LocalDate dateFinValidite
    ) {
    }
}
        package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.StatutAgentService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/statuts")
@CrossOrigin
public class StatutAgentController {

    private final StatutAgentService statutAgentService;

    public StatutAgentController(
            StatutAgentService statutAgentService) {
        this.statutAgentService = statutAgentService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllStatuts() {
        List<Map<String, Object>> statuts =
                statutAgentService.getAllStatuts();

        Map<String, Object> response = new HashMap<>();
        response.put("value", statuts);
        response.put("Count", statuts.size());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getStatutById(
            @PathVariable Long id) {

        Map<String, Object> statut =
                statutAgentService.getStatutById(id);

        if (statut == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(statut);
    }

    @GetMapping("/recherche")
    public ResponseEntity<Map<String, Object>> rechercherStatuts(
            @RequestParam String query) {

        List<Map<String, Object>> statuts =
                statutAgentService.rechercherStatuts(query);

        Map<String, Object> response = new HashMap<>();
        response.put("value", statuts);
        response.put("Count", statuts.size());

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> createStatut(
            @RequestBody StatutRequest request) {

        try {
            Map<String, Object> statut =
                    statutAgentService.createStatut(
                            request.code(),
                            request.libelle(),
                            request.description(),
                            request.dateDebutValidite(),
                            request.dateFinValidite()
                    );

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(statut);

        } catch (IllegalArgumentException e) {

            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());

            return ResponseEntity
                    .badRequest()
                    .body(error);

        } catch (DataIntegrityViolationException e) {

            Map<String, String> error = new HashMap<>();
            error.put(
                    "message",
                    "Impossible de créer ce statut. Une contrainte en base de données a été violée."
            );

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(error);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateStatut(
            @PathVariable Long id,
            @RequestBody StatutRequest request) {

        try {
            Map<String, Object> statut =
                    statutAgentService.updateStatut(
                            id,
                            request.code(),
                            request.libelle(),
                            request.description(),
                            request.dateDebutValidite(),
                            request.dateFinValidite()
                    );

            if (statut == null) {
                Map<String, String> error = new HashMap<>();
                error.put(
                        "message",
                        "Statut introuvable."
                );

                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(error);
            }

            return ResponseEntity.ok(statut);

        } catch (IllegalArgumentException e) {

            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());

            return ResponseEntity
                    .badRequest()
                    .body(error);

        } catch (DataIntegrityViolationException e) {

            Map<String, String> error = new HashMap<>();
            error.put(
                    "message",
                    "Impossible de modifier ce statut. Une contrainte en base de données a été violée."
            );

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(error);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteStatut(
            @PathVariable Long id) {

        try {
            boolean deleted =
                    statutAgentService.deleteStatut(id);

            if (!deleted) {
                Map<String, String> error = new HashMap<>();
                error.put(
                        "message",
                        "Statut introuvable."
                );

                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(error);
            }

            return ResponseEntity.noContent().build();

        } catch (IllegalArgumentException e) {

            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());

            return ResponseEntity
                    .badRequest()
                    .body(error);

        } catch (DataIntegrityViolationException e) {

            Map<String, String> error = new HashMap<>();
            error.put(
                    "message",
                    "Impossible de supprimer ce statut car il est utilisé ailleurs dans l'application."
            );

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(error);
        }
    }

    public record StatutRequest(
            String code,
            String libelle,
            String description,
            Date dateDebutValidite,
            Date dateFinValidite
    ) {
    }
}

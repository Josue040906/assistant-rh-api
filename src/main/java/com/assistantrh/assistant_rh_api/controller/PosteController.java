        package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.PosteService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/postes")
@CrossOrigin
public class PosteController {

    private final PosteService posteService;

    public PosteController(PosteService posteService) {
        this.posteService = posteService;
    }

    @GetMapping
    public ResponseEntity<?> getAllPostes() {

        List<Map<String, Object>> postes =
                posteService.getAllPostes();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("value", postes);
        response.put("Count", postes.size());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getPosteById(
            @PathVariable Long id
    ) {

        Map<String, Object> poste =
                posteService.getPosteById(id);

        if (poste == null) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "message",
                            "Poste introuvable."
                    ));
        }

        return ResponseEntity.ok(poste);
    }

    @GetMapping("/recherche")
    public ResponseEntity<?> rechercherPostes(
            @RequestParam String query
    ) {

        List<Map<String, Object>> postes =
                posteService.rechercherPostes(query);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("value", postes);
        response.put("Count", postes.size());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/service")
    public ResponseEntity<?> rechercherPostesParService(
            @RequestParam Long serviceId
    ) {

        List<Map<String, Object>> postes =
                posteService.rechercherPostesParService(serviceId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("value", postes);
        response.put("Count", postes.size());

        return ResponseEntity.ok(response);
    }


    @PostMapping
    public ResponseEntity<?> createPoste(
            @RequestBody PosteRequest request
    ) {

        try {

            Map<String, Object> poste =
                    posteService.createPoste(
                            request.intitule(),
                            request.description(),
                            request.serviceId()
                    );

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(poste);

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
                            "Impossible de créer ce poste. Vérifiez que le service existe."
                    ));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePoste(
            @PathVariable Long id,
            @RequestBody PosteRequest request
    ) {

        try {

            Map<String, Object> poste =
                    posteService.updatePoste(
                            id,
                            request.intitule(),
                            request.description(),
                            request.serviceId()
                    );

            if (poste == null) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "message",
                                "Poste introuvable."
                        ));
            }

            return ResponseEntity.ok(poste);

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
                            "Impossible de modifier ce poste. Vérifiez que le service existe."
                    ));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePoste(
            @PathVariable Long id
    ) {

        try {

            boolean deleted =
                    posteService.deletePoste(id);

            if (!deleted) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "message",
                                "Poste introuvable."
                        ));
            }

            return ResponseEntity.noContent().build();

        } catch (DataIntegrityViolationException e) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Ce poste ne peut pas être supprimé car il est encore utilisé par des agents."
                    ));
        }
    }

    public record PosteRequest(
            String intitule,
            String description,
            Long serviceId
    ) {}
}

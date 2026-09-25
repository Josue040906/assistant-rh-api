package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.DirectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/directions")
@CrossOrigin
public class DirectionController {

    private final DirectionService directionService;

    public DirectionController(DirectionService directionService) {
        this.directionService = directionService;
    }

    @GetMapping
    public ResponseEntity<?> getAllDirections() {
        var directions = directionService.getAllDirections();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("value", directions);
        response.put("Count", directions.size());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDirectionById(
            @PathVariable Long id
    ) {
        Map<String, Object> direction =
                directionService.getDirectionById(id);

        if (direction == null) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "message",
                            "Direction introuvable."
                    ));
        }

        return ResponseEntity.ok(direction);
    }

    @PostMapping
    public ResponseEntity<?> createDirection(
            @RequestBody DirectionRequest request
    ) {
        try {
            Map<String, Object> direction =
                    directionService.createDirection(
                            request.nom(),
                            request.description()
                    );

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(direction);

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "message",
                            e.getMessage()
                    ));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateDirection(
            @PathVariable Long id,
            @RequestBody DirectionRequest request
    ) {
        try {
            Map<String, Object> direction =
                    directionService.updateDirection(
                            id,
                            request.nom(),
                            request.description()
                    );

            if (direction == null) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "message",
                                "Direction introuvable."
                        ));
            }

            return ResponseEntity.ok(direction);

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .badRequest()
                    .body(Map.of(
                            "message",
                            e.getMessage()
                    ));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDirection(
            @PathVariable Long id
    ) {
        try {
            boolean deleted =
                    directionService.deleteDirection(id);

            if (!deleted) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "message",
                                "Direction introuvable."
                        ));
            }

            return ResponseEntity.noContent().build();

        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Cette direction ne peut pas être supprimée car elle est encore utilisée par des services."
                    ));
        }
    }

    public record DirectionRequest(
            String nom,
            String description
    ) {}

}

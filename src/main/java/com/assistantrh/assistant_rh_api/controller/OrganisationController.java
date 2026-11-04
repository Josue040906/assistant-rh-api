package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.OrganisationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/organisation")
public class OrganisationController {

    private final OrganisationService organisationService;

    public OrganisationController(OrganisationService organisationService) {
        this.organisationService = organisationService;
    }

    @GetMapping
    public ResponseEntity<?> consulter(
            @RequestParam String operation,
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) Long directionId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) String query
    ) {
        try {
            Object resultat = organisationService.executer(
                    operation, id, directionId, serviceId, query
            );
            return ResponseEntity.ok(resultat);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (OrganisationService.RessourceOrganisationIntrouvableException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        } catch (UnsupportedOperationException e) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}

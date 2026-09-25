        package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.ServiceService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/services")
@CrossOrigin
public class ServiceController {

    private final ServiceService serviceService;

    public ServiceController(ServiceService serviceService) {
        this.serviceService = serviceService;
    }

    @GetMapping
    public ResponseEntity<?> getAllServices() {
        List<Map<String, Object>> services =
                serviceService.getAllServices();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("value", services);
        response.put("Count", services.size());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getServiceById(
            @PathVariable Long id
    ) {
        Map<String, Object> service =
                serviceService.getServiceById(id);

        if (service == null) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "message",
                            "Service introuvable."
                    ));
        }

        return ResponseEntity.ok(service);
    }

    @GetMapping("/recherche")
    public ResponseEntity<?> rechercherServices(
            @RequestParam String query
    ) {
        List<Map<String, Object>> services =
                serviceService.rechercherServices(query);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("value", services);
        response.put("Count", services.size());

        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> createService(
            @RequestBody ServiceRequest request
    ) {
        try {
            Map<String, Object> service =
                    serviceService.createService(
                            request.code(),
                            request.nom(),
                            request.description(),
                            request.directionId()
                    );

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(service);

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
                            "Impossible de créer ce service. Vérifiez que le code est unique et que la direction existe."
                    ));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateService(
            @PathVariable Long id,
            @RequestBody ServiceRequest request
    ) {
        try {
            Map<String, Object> service =
                    serviceService.updateService(
                            id,
                            request.code(),
                            request.nom(),
                            request.description(),
                            request.directionId()
                    );

            if (service == null) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "message",
                                "Service introuvable."
                        ));
            }

            return ResponseEntity.ok(service);

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
                            "Impossible de modifier ce service. Vérifiez que le code est unique et que la direction existe."
                    ));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteService(
            @PathVariable Long id
    ) {
        try {
            boolean deleted =
                    serviceService.deleteService(id);

            if (!deleted) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "message",
                                "Service introuvable."
                        ));
            }

            return ResponseEntity.noContent().build();

        } catch (DataIntegrityViolationException e) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Ce service ne peut pas être supprimé car il est encore utilisé par des postes ou des agents."
                    ));
        }
    }

    public record ServiceRequest(
            String code,
            String nom,
            String description,
            Long directionId
    ) {}
}

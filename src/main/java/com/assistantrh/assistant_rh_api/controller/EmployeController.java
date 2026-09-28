        package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.EmployeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/employes")
public class EmployeController {

    private final EmployeService employeService;

    public EmployeController(EmployeService employeService) {
        this.employeService = employeService;
    }

    @GetMapping
    public List<Map<String, Object>> getAllEmployes() {
        return employeService.getAllEmployes();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getEmployeById(
            @PathVariable Integer id
    ) {
        return employeService.getEmployeById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/profil")
    public Optional<Map<String, Object>> rechercherProfil(
            @RequestParam String query
    ) {
        return employeService.rechercherProfilEmploye(query);
    }

    @GetMapping("/recherche")
    public List<Map<String, Object>> rechercherEmployes(
            @RequestParam String query
    ) {
        return employeService.rechercherEmployes(query);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> creerEmploye(
            @RequestBody EmployeCreateRequest request
    ) {

        Integer employeId = employeService.creerEmploye(
                request.matricule(),
                request.nom(),
                request.prenom(),
                request.sexe(),
                request.adresse(),
                request.cin(),
                request.telephone(),
                request.dateNaissance(),
                request.lieuNaissance(),
                request.dateEmbauche(),
                request.posteId(),
                request.serviceId(),
                request.typeEmploiId(),
                request.categorieId(),
                request.gradeId(),
                request.lieuTravail(),
                request.photo(),
                request.userId()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Map.of(
                        "id", employeId,
                        "message", "Agent créé avec succès."
                ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> modifierEmploye(
            @PathVariable Integer id,
            @RequestBody EmployeUpdateRequest request
    ) {

        employeService.modifierEmploye(
                id,
                request.nom(),
                request.prenom(),
                request.sexe(),
                request.adresse(),
                request.cin(),
                request.telephone(),
                request.dateNaissance(),
                request.lieuNaissance(),
                request.dateEmbauche(),
                request.lieuTravail(),
                request.photo()
        );

        return ResponseEntity.ok(
                Map.of(
                        "id", id,
                        "message", "Agent modifié avec succès."
                )
        );
    }

    @PostMapping(
            value = "/{id}/photo",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<Map<String, Object>> uploaderPhoto(
            @PathVariable Integer id,
            @RequestParam("file") MultipartFile file
    ) {

        String photoPath =
                employeService.enregistrerPhoto(
                        id,
                        file
                );

        return ResponseEntity.ok(
                Map.of(
                        "id", id,
                        "photo", photoPath,
                        "message", "Photo enregistrée avec succès."
                )
        );
    }

}

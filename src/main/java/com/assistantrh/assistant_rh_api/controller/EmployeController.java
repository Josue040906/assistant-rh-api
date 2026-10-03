package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.AffectationService;
import com.assistantrh.assistant_rh_api.service.EmployeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/employes")
public class EmployeController {

    private final EmployeService employeService;
    private final AffectationService affectationService;

    public EmployeController(
            EmployeService employeService,
            AffectationService affectationService
    ) {
        this.employeService = employeService;
        this.affectationService = affectationService;
    }

    @GetMapping
    public List<Map<String, Object>> getAllEmployes() {
        return employeService.getAllEmployes();
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

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<Map<String, Object>> getEmployeById(
            @PathVariable Integer id
    ) {
        return employeService
                .getEmployeById(id)
                .map(ResponseEntity::ok)
                .orElseGet(
                        () -> ResponseEntity.notFound().build()
                );
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> creerEmploye(
            @RequestBody EmployeCreateRequest request
    ) {

        Integer employeId =
                employeService.creerEmploye(
                        request.acteurId(),
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
                        request.lieuTravail(),
                        request.photo(),
                        request.userId()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        Map.of(
                                "id", employeId,
                                "message",
                                "Agent créé avec succès."
                        )
                );
    }

    @PutMapping("/{id:\\d+}")
    public ResponseEntity<Map<String, Object>> modifierEmploye(
            @PathVariable Integer id,
            @RequestBody EmployeUpdateRequest request
    ) {

        employeService.modifierEmploye(
                id,
                request.acteurId(),
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
                        "message",
                        "Agent modifié avec succès."
                )
        );
    }

    @PutMapping("/{id:\\d+}/affectation")
    public ResponseEntity<Map<String, Object>> modifierAffectation(
            @PathVariable Integer id,
            @RequestBody AffectationUpdateRequest request
    ) {

        affectationService.modifierAffectation(
                id,
                request.acteurId(),
                request.posteId(),
                request.serviceId(),
                request.lieuTravail(),
                request.dateEffet(),
                request.referenceActe(),
                request.observation()
        );

        return ResponseEntity.ok(
                Map.of(
                        "id", id,
                        "message",
                        "Affectation modifiée avec succès."
                )
        );
    }

    @GetMapping("/{id:\\d+}/affectation")
    public ResponseEntity<Map<String, Object>> getAffectationActuelle(
            @PathVariable Integer id
    ) {

        Map<String, Object> affectation =
                affectationService.getAffectationActuelle(id);

        if (affectation == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(affectation);
    }

    @GetMapping("/{id:\\d+}/affectations")
    public List<Map<String, Object>> getHistoriqueAffectations(
            @PathVariable Integer id
    ) {
        return affectationService.getHistorique(id);
    }

    @PostMapping(
            value = "/{id:\\d+}/photo",
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
                        "message",
                        "Photo enregistrée avec succès."
                )
        );
    }
}
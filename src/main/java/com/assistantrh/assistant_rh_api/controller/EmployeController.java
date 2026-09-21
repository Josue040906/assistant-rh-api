
        package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.EmployeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}

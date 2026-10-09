package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.repository.EmployeReferentielRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/employes/referentiels")
public class EmployeReferentielController {

    private final EmployeReferentielRepository referentielRepository;

    public EmployeReferentielController(
            EmployeReferentielRepository referentielRepository
    ) {
        this.referentielRepository = referentielRepository;
    }

    @GetMapping
    public Map<String, Object> getReferentiels() {
        return Map.of(
                "typesEmploi", referentielRepository.findAllTypesEmploi(),
                "categories", referentielRepository.findAllCategories()
        );
    }
}

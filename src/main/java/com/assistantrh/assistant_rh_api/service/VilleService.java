package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.model.Ville;
import com.assistantrh.assistant_rh_api.repository.VilleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VilleService {

    private final VilleRepository repository;

    public VilleService(VilleRepository repository) {
        this.repository = repository;
    }

    public List<Ville> obtenirVilles() {
        return repository.findAll();
    }

    public String validerVille(String nom, String libelle) {
        if (nom == null || nom.isBlank()) {
            return null;
        }
        return repository.findName(nom.trim())
                .orElseThrow(() -> new IllegalArgumentException(
                        libelle + " doit être sélectionné dans la liste des villes."
                ));
    }
}

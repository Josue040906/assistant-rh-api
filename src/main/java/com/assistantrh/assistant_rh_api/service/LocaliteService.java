package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.model.Localite;
import com.assistantrh.assistant_rh_api.repository.LocaliteRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LocaliteService {

    private static final int TAILLE_PAGE_MAXIMALE = 100;

    private final LocaliteRepository repository;

    public LocaliteService(LocaliteRepository repository) {
        this.repository = repository;
    }

    public List<Localite> regions() {
        return repository.findRegions();
    }

    public List<Localite> districts(long regionId) {
        return enfants(regionId, "REGION", "DISTRICT");
    }

    public List<Localite> communes(long districtId) {
        return enfants(districtId, "DISTRICT", "COMMUNE");
    }

    public List<Localite> quartiers(long communeId) {
        return enfants(communeId, "COMMUNE", "FOKONTANY");
    }

    public LocaliteDetail detail(long id) {
        if (id <= 0) {
            throw new LocaliteInvalideException(
                    "L'identifiant de la localité doit être positif."
            );
        }
        List<Localite> chemin = repository.findPath(id);
        if (chemin.isEmpty()) {
            throw new LocaliteInvalideException(
                    "La localité demandée n'existe pas."
            );
        }
        return new LocaliteDetail(chemin.get(chemin.size() - 1), chemin);
    }

    public PageResultat recherche(String query, int page, int size) {
        if (query == null || query.isBlank()) {
            throw new LocaliteInvalideException(
                    "Le texte de recherche est obligatoire."
            );
        }
        if (page < 0 || size < 1 || size > TAILLE_PAGE_MAXIMALE) {
            throw new LocaliteInvalideException(
                    "La pagination est invalide (size doit être compris entre 1 et 100)."
            );
        }
        if (page > Integer.MAX_VALUE / size) {
            throw new LocaliteInvalideException(
                    "La page demandée est trop élevée."
            );
        }
        String recherche = query.trim();
        long total = repository.countSearch(recherche);
        return new PageResultat(
                page,
                size,
                total,
                repository.search(recherche, size, Math.multiplyExact(page, size))
        );
    }

    public void validerLocaliteResidence(Long id) {
        if (id != null && (id <= 0 || !repository.existsSelectableById(id))) {
            throw new LocaliteInvalideException(
                    "La localité de résidence doit être une commune "
                            + "ou un fokontany existant."
            );
        }
    }

    private List<Localite> enfants(long parentId, String niveauParent, String niveau) {
        if (parentId <= 0) {
            throw new LocaliteInvalideException(
                    "L'identifiant de la localité parente doit être positif."
            );
        }
        Localite parent = repository.findById(parentId)
                .orElseThrow(() -> new LocaliteInvalideException(
                        "La localité parente demandée n'existe pas."
                ));
        if (!niveauParent.equals(parent.niveau())) {
            throw new LocaliteInvalideException(
                    "L'identifiant fourni ne correspond pas au niveau attendu."
            );
        }
        return repository.findChildren(parentId, niveau);
    }

    public record LocaliteDetail(
            Localite localite,
            List<Localite> cheminAdministratif
    ) {
    }

    public record PageResultat(
            int page,
            int size,
            long total,
            List<Localite> resultats
    ) {
    }
}

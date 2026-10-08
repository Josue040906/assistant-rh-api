package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.model.Activite;
import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import com.assistantrh.assistant_rh_api.repository.ActiviteRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ActiviteService {

    private static final Map<String, String> TYPES_ACTION = Map.ofEntries(
            Map.entry("CREATION_AGENT", "CREATION"),
            Map.entry("CREATION_COMPTE", "CREATION"),
            Map.entry("NOUVEAU_DOCUMENT", "CREATION"),
            Map.entry("MODIFICATION_AGENT", "MODIFICATION"),
            Map.entry("MODIFICATION_PROFIL", "MODIFICATION"),
            Map.entry("MODIFICATION_AFFECTATION", "MODIFICATION"),
            Map.entry("MODIFICATION_DOCUMENT", "MODIFICATION"),
            Map.entry("ARCHIVAGE_DOCUMENT", "MODIFICATION"),
            Map.entry("APPROBATION_COMPTE", "VALIDATION"),
            Map.entry("REFUS_COMPTE", "REFUS"),
            Map.entry("GENERATION_DOCUMENT", "GENERATION"),
            Map.entry("TELEVERSEMENT_PHOTO", "TELEVERSEMENT")
    );
    private static final Set<String> TYPES_ACTION_VALIDES = Set.of(
            "CONNEXION",
            "DECONNEXION",
            "CREATION",
            "MODIFICATION",
            "SUPPRESSION",
            "VALIDATION",
            "REFUS",
            "GENERATION",
            "TELEVERSEMENT"
    );

    private final ActiviteRepository activiteRepository;

    public ActiviteService(ActiviteRepository activiteRepository) {
        this.activiteRepository = activiteRepository;
    }

    public void enregistrer(
            Integer acteurId,
            Integer employeId,
            String typeAction,
            String description
    ) {
        if (acteurId != null && acteurId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'acteur doit être positif."
            );
        }
        if (employeId != null && employeId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'agent concerné doit être positif."
            );
        }
        if (typeAction == null || typeAction.isBlank()) {
            throw new IllegalArgumentException(
                    "Le type de l'activité est obligatoire."
            );
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException(
                    "La description de l'activité est obligatoire."
            );
        }

        Integer acteurAuthentifie = lireActeurAuthentifie();
        Integer acteurEffectif = acteurAuthentifie == null
                ? acteurId
                : acteurAuthentifie;
        String typeNormalise = normaliserTypeAction(typeAction);

        activiteRepository.enregistrer(
                acteurEffectif,
                employeId,
                typeNormalise,
                description.trim()
        );
    }

    public List<Activite> listerParActeur(Integer acteurId) {
        if (acteurId == null || acteurId <= 0) {
            throw new IllegalArgumentException(
                    "L'identifiant de l'utilisateur connecté est invalide."
            );
        }
        return activiteRepository.findByActeurId(acteurId);
    }

    private Integer lireActeurAuthentifie() {
        if (!(RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes)) {
            return null;
        }

        HttpServletRequest request = attributes.getRequest();
        Object acteurId = request.getAttribute(
                ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE
        );
        return acteurId instanceof Integer id ? id : null;
    }

    private String normaliserTypeAction(String typeAction) {
        String normalise = typeAction.trim().toUpperCase(Locale.ROOT);
        String standardise = TYPES_ACTION.getOrDefault(normalise, normalise);
        if (!TYPES_ACTION_VALIDES.contains(standardise)) {
            throw new IllegalArgumentException(
                    "Le type d'activité n'est pas reconnu."
            );
        }
        return standardise;
    }
}
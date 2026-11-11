package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.PosteRepository;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PosteService {

    private final PosteRepository posteRepository;

    public PosteService(PosteRepository posteRepository) {
        this.posteRepository = posteRepository;
    }

    public List<Map<String, Object>> getAllPostes() {
        return posteRepository.findAll();
    }

    public Map<String, Object> getPosteById(Long id) {
        return posteRepository.findById(id);
    }

    public List<Map<String, Object>> rechercherPostes(String query) {

        if (query == null || query.isBlank()) {
            return List.of();
        }

        return posteRepository.search(query.trim());
    }

    public List<Map<String, Object>> rechercherPostesParService(
            Long serviceId
    ) {

        if (serviceId == null) {
            return List.of();
        }

        return etatDesPostes(serviceId, null);
    }

    /**
     * Les postes (d'un service, d'une direction, ou tous) et leur état : places,
     * occupants, places libres, et le premier départ annoncé dans l'année
     * (demande de retraite, ou droit à la retraite atteint d'ici douze mois).
     * statut : LIBRE (au moins une place libre), SE_LIBERE (complet, un départ
     * annoncé), COMPLET.
     */
    public List<Map<String, Object>> etatDesPostes(Long serviceId, Long directionId) {
        LocalDate aujourdhui = LocalDate.now();
        LocalDate horizon = aujourdhui.plusMonths(12);
        Map<Object, List<Map<String, Object>>> parPoste = new LinkedHashMap<>();
        for (Map<String, Object> occupant : posteRepository.occupants(serviceId, directionId)) {
            Map<String, Object> agent = new LinkedHashMap<>(occupant);
            Object posteId = agent.remove("poste_id");
            LocalDate demande = date(agent.remove("depart_demande"));
            LocalDate droit = date(agent.remove("retraite_possible"));
            LocalDate depart = demande != null ? demande
                    : droit != null && !droit.isAfter(horizon) ? (droit.isBefore(aujourdhui) ? aujourdhui : droit) : null;
            agent.put("depart_prevu", depart);
            agent.put("motif_depart", demande != null ? "DEMANDE_RETRAITE" : depart != null ? "DROIT_RETRAITE" : null);
            parPoste.computeIfAbsent(posteId, cle -> new ArrayList<>()).add(agent);
        }
        List<Map<String, Object>> postes = new ArrayList<>();
        for (Map<String, Object> ligne : posteRepository.postesAvecPlaces(serviceId, directionId)) {
            Map<String, Object> poste = new LinkedHashMap<>(ligne);
            List<Map<String, Object>> occupants = parPoste.getOrDefault(poste.get("id"), List.of());
            int places = ((Number) poste.get("nombre_places")).intValue();
            int libres = Math.max(0, places - occupants.size());
            LocalDate seLibere = occupants.stream()
                    .map(occupant -> (LocalDate) occupant.get("depart_prevu"))
                    .filter(java.util.Objects::nonNull)
                    .min(LocalDate::compareTo).orElse(null);
            poste.put("occupes", occupants.size());
            poste.put("libres", libres);
            poste.put("se_libere_le", seLibere);
            poste.put("statut", libres > 0 ? "LIBRE" : seLibere != null ? "SE_LIBERE" : "COMPLET");
            poste.put("occupants", occupants);
            postes.add(poste);
        }
        return postes;
    }

    /** Qui pourrait reprendre ce poste (même direction, catégorie au moins égale) : une aide, l'humain décide. */
    public List<Map<String, Object>> candidatsReleve(Long posteId) {
        if (posteId == null || posteRepository.findById(posteId) == null) {
            throw new IllegalArgumentException("Le poste demandé n'existe pas.");
        }
        return posteRepository.candidatsReleve(posteId);
    }

    private static LocalDate date(Object valeur) {
        if (valeur instanceof LocalDate jour) {
            return jour;
        }
        return valeur instanceof Date sql ? sql.toLocalDate() : null;
    }


    public Map<String, Object> createPoste(
            String intitule,
            String description,
            Long serviceId,
            Integer nombrePlaces
    ) {
        validateIntitule(intitule);
        validateServiceId(serviceId);

        return posteRepository.create(
                intitule.trim(),
                normalizeDescription(description),
                serviceId,
                places(nombrePlaces, 0)
        );
    }

    public Map<String, Object> updatePoste(
            Long id,
            String intitule,
            String description,
            Long serviceId,
            Integer nombrePlaces
    ) {
        validateIntitule(intitule);
        validateServiceId(serviceId);

        Map<String, Object> existing =
                posteRepository.findById(id);

        if (existing == null) {
            return null;
        }
        int occupes = etatDesPostes(null, null).stream()
                .filter(poste -> String.valueOf(id).equals(String.valueOf(poste.get("id"))))
                .mapToInt(poste -> ((Number) poste.get("occupes")).intValue())
                .findFirst().orElse(0);

        return posteRepository.update(
                id,
                intitule.trim(),
                normalizeDescription(description),
                serviceId,
                places(nombrePlaces == null ? ((Number) existing.getOrDefault("nombre_places", 1)).intValue()
                        : nombrePlaces, occupes)
        );
    }

    public boolean deletePoste(Long id) {
        Map<String, Object> existing =
                posteRepository.findById(id);

        if (existing == null) {
            return false;
        }

        return posteRepository.delete(id);
    }

    /** Au moins une place, et jamais moins que les agents qui occupent déjà le poste. */
    private static int places(Integer nombrePlaces, int occupes) {
        int valeur = nombrePlaces == null ? 1 : nombrePlaces;
        if (valeur < 1 || valeur > 500) {
            throw new IllegalArgumentException("Le nombre de places doit être compris entre 1 et 500.");
        }
        if (valeur < occupes) {
            throw new IllegalArgumentException(occupes + " agents occupent ce poste : il lui faut au moins "
                    + occupes + " places.");
        }
        return valeur;
    }

    private void validateIntitule(String intitule) {
        if (intitule == null || intitule.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "L'intitulé du poste est obligatoire."
            );
        }

        if (intitule.trim().length() > 200) {
            throw new IllegalArgumentException(
                    "L'intitulé du poste ne doit pas dépasser 200 caractères."
            );
        }
    }

    private void validateServiceId(Long serviceId) {
        if (serviceId == null) {
            throw new IllegalArgumentException(
                    "Le service du poste est obligatoire."
            );
        }
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }

        String value = description.trim();

        return value.isEmpty() ? null : value;
    }
}

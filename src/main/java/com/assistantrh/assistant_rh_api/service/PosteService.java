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
        return etatDesPostes(serviceId, directionId, LocalDate.now().plusMonths(12));
    }

    /**
     * Le même état, avec les départs prévus jusqu'à « horizon » : une demande de
     * retraite en cours (sans réponse ou acceptée), sinon le droit au départ atteint
     * d'ici là (un droit déjà dépassé compte pour aujourd'hui). Une seule source
     * pour l'organigramme et pour le plan de relève.
     */
    public List<Map<String, Object>> etatDesPostes(Long serviceId, Long directionId, LocalDate horizon) {
        LocalDate aujourdhui = LocalDate.now();
        Map<Object, List<Map<String, Object>>> parPoste = new LinkedHashMap<>();
        for (Map<String, Object> occupant : posteRepository.occupants(serviceId, directionId)) {
            Map<String, Object> agent = new LinkedHashMap<>(occupant);
            Object posteId = agent.remove("poste_id");
            LocalDate demande = date(agent.remove("depart_demande"));
            LocalDate droit = date(agent.remove("retraite_possible"));
            LocalDate depart = demande != null ? demande
                    : droit != null && !droit.isAfter(horizon) ? (droit.isBefore(aujourdhui) ? aujourdhui : droit) : null;
            agent.put("depart_prevu", depart);
            // Le jour du droit au départ, même dépassé (« possible depuis le … »).
            agent.put("retraite_possible_le", droit);
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

    /**
     * Qui pourrait reprendre ce poste : les six meilleurs candidats de la même
     * direction, chacun avec son rang et ses raisons en clair (même service,
     * catégorie, compétences et diplôme en commun avec les titulaires, formations,
     * ancienneté). Celui qui part lui-même moins de deux ans après les titulaires
     * n'est pas proposé. Une aide : l'humain décide.
     */
    public List<Map<String, Object>> candidatsReleve(Long posteId) {
        if (posteId == null || posteRepository.findById(posteId) == null) {
            throw new IllegalArgumentException("Le poste demandé n'existe pas.");
        }
        LocalDate aujourdhui = LocalDate.now();
        List<Map<String, Object>> titulaires = posteRepository.titulairesDuPoste(posteId);
        // Le poste se libère au premier départ d'un titulaire (aujourd'hui si le droit est déjà là).
        LocalDate liberation = titulaires.stream().map(titulaire -> date(titulaire.get("retraite_possible")))
                .filter(java.util.Objects::nonNull).min(LocalDate::compareTo)
                .map(jour -> jour.isBefore(aujourdhui) ? aujourdhui : jour).orElse(aujourdhui);
        List<Map<String, Object>> candidats = posteRepository.candidatsReleve(posteId).stream()
                .filter(candidat -> {
                    LocalDate depart = date(candidat.get("retraite_possible"));
                    return depart == null || !depart.isBefore(liberation.plusYears(2));
                }).toList();

        List<Integer> titulaireIds = titulaires.stream().map(t -> ((Number) t.get("id")).intValue()).toList();
        List<Integer> candidatIds = candidats.stream().map(c -> ((Number) c.get("id")).intValue()).toList();
        List<Integer> tous = new ArrayList<>(titulaireIds);
        tous.addAll(candidatIds);
        Map<Integer, List<String>> competences = parAgent(posteRepository.competences(tous), "libelle");
        Map<Integer, List<String>> domaines = parAgent(posteRepository.diplomes(tous), "domaine");
        Map<Integer, List<String>> formations = parAgent(posteRepository.formations(candidatIds), "intitule");

        java.util.Set<String> competencesDuPoste = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        java.util.Set<String> domainesDuPoste = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        titulaireIds.forEach(id -> {
            competencesDuPoste.addAll(competences.getOrDefault(id, List.of()));
            domainesDuPoste.addAll(domaines.getOrDefault(id, List.of()));
        });

        List<Map<String, Object>> classes = new ArrayList<>();
        for (Map<String, Object> ligne : candidats) {
            Map<String, Object> candidat = new LinkedHashMap<>(ligne);
            int id = ((Number) ligne.get("id")).intValue();
            int score = 0;
            List<String> raisons = new ArrayList<>();
            if (Boolean.TRUE.equals(ligne.get("meme_service"))) {
                score += 40;
                raisons.add("Même service");
            } else {
                score += 10;
                raisons.add("Même direction, " + ligne.get("service"));
            }
            Object requise = ligne.get("categorie_requise");
            if (requise != null && java.util.Objects.equals(ligne.get("categorie_id"), ligne.get("categorie_min"))) {
                score += 10;
                raisons.add("Catégorie " + ligne.get("categorie") + ", comme le poste");
            } else if (requise != null) {
                score += 6;
                raisons.add("Catégorie " + ligne.get("categorie") + " (le poste demande " + requise + ")");
            }
            List<String> communes = competences.getOrDefault(id, List.of()).stream()
                    .filter(competencesDuPoste::contains).distinct().toList();
            if (!communes.isEmpty()) {
                score += 8 * Math.min(3, communes.size());
                raisons.add(communes.size() + (communes.size() > 1 ? " compétences" : " compétence")
                        + " en commun avec le titulaire : " + String.join(", ", communes.stream().limit(3).toList()));
            }
            domaines.getOrDefault(id, List.of()).stream().filter(java.util.Objects::nonNull)
                    .filter(domainesDuPoste::contains).findFirst().ifPresent(domaine -> {
                        raisons.add("Diplôme en " + domaine + ", comme le titulaire");
                    });
            if (domaines.getOrDefault(id, List.of()).stream().anyMatch(domainesDuPoste::contains)) {
                score += 12;
            }
            List<String> suivies = formations.getOrDefault(id, List.of());
            if (!suivies.isEmpty()) {
                score += 3 * Math.min(2, suivies.size());
                raisons.add(suivies.size() == 1 ? "Formation : " + suivies.get(0)
                        : suivies.size() + " formations, dont " + suivies.get(0));
            }
            LocalDate embauche = date(ligne.get("date_embauche"));
            if (embauche != null) {
                long annees = java.time.temporal.ChronoUnit.YEARS.between(embauche, aujourdhui);
                if (annees >= 5) {
                    score += 5;
                    raisons.add(annees + " ans de service");
                }
            }
            candidat.put("score", score);
            candidat.put("raisons", raisons);
            candidat.remove("categorie_min");
            candidat.remove("categorie_id");
            classes.add(candidat);
        }
        classes.sort(java.util.Comparator.<Map<String, Object>>comparingInt(c -> -((Integer) c.get("score")))
                .thenComparing(c -> -(c.get("indice") instanceof Number indice ? indice.intValue() : 0))
                .thenComparing(c -> String.valueOf(c.get("nom"))));
        return classes.stream().limit(6).toList();
    }

    private static Map<Integer, List<String>> parAgent(List<Map<String, Object>> lignes, String champ) {
        Map<Integer, List<String>> resultat = new LinkedHashMap<>();
        for (Map<String, Object> ligne : lignes) {
            if (ligne.get(champ) != null) {
                resultat.computeIfAbsent(((Number) ligne.get("employe_id")).intValue(), cle -> new ArrayList<>())
                        .add(ligne.get(champ).toString());
            }
        }
        return resultat;
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

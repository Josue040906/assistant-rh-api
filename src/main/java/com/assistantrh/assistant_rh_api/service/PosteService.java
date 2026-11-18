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

    /** Les exigences des postes (facultatif : absent dans les tests unitaires qui n'en ont pas besoin). */
    private ExigencePosteService exigences;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setExigences(ExigencePosteService exigences) {
        this.exigences = exigences;
    }

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
     * Qui pourrait occuper ce poste : les six agents qui lui correspondent le mieux,
     * chacun avec ses atouts (raisons) et ses points d'attention. Une aide seulement :
     * l'humain choisit qui il veut, même en dehors de cette liste (analyserCandidat).
     */
    public List<Map<String, Object>> candidatsReleve(Long posteId) {
        return evaluerPourPoste(posteId, null).stream().limit(6).toList();
    }

    /** L'analyse d'un agent choisi librement pour ce poste, avec les mêmes critères. */
    public Map<String, Object> analyserCandidat(Long posteId, Integer employeId) {
        if (employeId == null) {
            throw new IllegalArgumentException("Choisissez un agent.");
        }
        List<Map<String, Object>> resultat = evaluerPourPoste(posteId, employeId);
        if (resultat.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cet agent ne peut pas être choisi : il occupe déjà ce poste, ou il n'est plus en activité.");
        }
        return resultat.get(0);
    }

    private static boolean posteDeResponsable(Object intitule) {
        String texte = String.valueOf(intitule).strip().toLowerCase(java.util.Locale.FRENCH);
        return texte.startsWith("chef") || texte.startsWith("directeur") || texte.startsWith("directrice")
                || texte.startsWith("responsable");
    }

    private static final java.time.format.DateTimeFormatter JOUR =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Évalue les agents pour un poste (tous, ou un seul). Atouts : a déjà occupé ce
     * poste, même service ou direction, catégorie, compétences et diplôme en commun
     * avec le titulaire, formations, ancienneté. Points d'attention (ils baissent le
     * classement sans exclure) : retraite proche, déjà choisi pour un autre poste,
     * son départ laisserait son propre poste sans titulaire, catégorie inférieure.
     */
    private List<Map<String, Object>> evaluerPourPoste(Long posteId, Integer seulAgent) {
        Map<String, Object> poste = posteId == null ? null : posteRepository.findById(posteId);
        if (poste == null) {
            throw new IllegalArgumentException("Le poste demandé n'existe pas.");
        }
        LocalDate aujourdhui = LocalDate.now();
        List<Map<String, Object>> titulaires = posteRepository.titulairesDuPoste(posteId);
        int places = poste.get("nombre_places") instanceof Number nombre ? nombre.intValue() : 1;
        // Une place déjà libre l'est aujourd'hui ; sinon le poste se libère au premier départ d'un titulaire.
        LocalDate liberation = titulaires.size() < places ? aujourdhui
                : titulaires.stream().map(titulaire -> date(titulaire.get("retraite_possible")))
                .filter(java.util.Objects::nonNull).min(LocalDate::compareTo)
                .map(jour -> jour.isBefore(aujourdhui) ? aujourdhui : jour).orElse(aujourdhui);
        List<Map<String, Object>> agents = posteRepository.agentsPourPoste(posteId, seulAgent);

        List<Integer> titulaireIds = titulaires.stream().map(t -> ((Number) t.get("id")).intValue()).toList();
        List<Integer> agentIds = agents.stream().map(c -> ((Number) c.get("id")).intValue()).toList();
        List<Integer> tous = new ArrayList<>(titulaireIds);
        tous.addAll(agentIds);
        Map<Integer, List<String>> competences = parAgent(posteRepository.competences(tous), "libelle");
        Map<Integer, List<String>> domaines = parAgent(posteRepository.diplomes(tous), "domaine");
        Map<Integer, List<String>> formations = parAgent(posteRepository.formations(agentIds), "intitule");
        Map<Integer, List<Map<String, Object>>> exigencesParAgent = exigences == null
                ? Map.of() : exigences.evaluer(posteId, agentIds);

        java.util.Set<String> competencesDuPoste = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        java.util.Set<String> domainesDuPoste = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        titulaireIds.forEach(id -> {
            competencesDuPoste.addAll(competences.getOrDefault(id, List.of()));
            domainesDuPoste.addAll(domaines.getOrDefault(id, List.of()).stream().filter(java.util.Objects::nonNull).toList());
        });

        List<Map<String, Object>> classes = new ArrayList<>();
        for (Map<String, Object> ligne : agents) {
            Map<String, Object> candidat = new LinkedHashMap<>(ligne);
            int id = ((Number) ligne.get("id")).intValue();
            int score = 0;
            List<String> raisons = new ArrayList<>();
            List<String> attentions = new ArrayList<>();

            // Le plus parlant : il connaît déjà ce poste.
            LocalDate occupeJusquau = date(ligne.get("deja_occupe_jusquau"));
            if (occupeJusquau != null) {
                LocalDate occupeDepuis = date(ligne.get("deja_occupe_depuis"));
                score += 45;
                raisons.add("A déjà occupé ce poste"
                        + (occupeDepuis != null ? " (" + occupeDepuis.getYear() + "–" + occupeJusquau.getYear() + ")" : ""));
            }

            if (Boolean.TRUE.equals(ligne.get("meme_service"))) {
                score += 40;
                raisons.add("Même service");
            } else if (Boolean.TRUE.equals(ligne.get("meme_direction"))) {
                score += 15;
                raisons.add("Même direction, " + ligne.get("service"));
            }

            if (ligne.get("categorie_id") instanceof Number categorie && ligne.get("categorie_min") instanceof Number requise) {
                if (categorie.intValue() == requise.intValue()) {
                    score += 10;
                    raisons.add("Catégorie " + ligne.get("categorie") + ", comme le poste");
                } else if (categorie.intValue() > requise.intValue()) {
                    score += 6;
                    raisons.add("Catégorie " + ligne.get("categorie") + ", au-dessus de celle du poste ("
                            + ligne.get("categorie_requise") + ")");
                } else {
                    attentions.add("Catégorie " + ligne.get("categorie") + ", en dessous de celle du poste ("
                            + ligne.get("categorie_requise") + ")");
                }
            }

            List<String> communes = competences.getOrDefault(id, List.of()).stream()
                    .filter(competencesDuPoste::contains).distinct().toList();
            if (!communes.isEmpty()) {
                score += 8 * Math.min(3, communes.size());
                raisons.add(communes.size() + (communes.size() > 1 ? " compétences" : " compétence")
                        + " en commun avec le titulaire : " + String.join(", ", communes.stream().limit(3).toList()));
            }
            domaines.getOrDefault(id, List.of()).stream().filter(java.util.Objects::nonNull)
                    .filter(domainesDuPoste::contains).findFirst().ifPresent(domaine ->
                            raisons.add("Diplôme en " + domaine + ", comme le titulaire"));
            if (domaines.getOrDefault(id, List.of()).stream().filter(java.util.Objects::nonNull)
                    .anyMatch(domainesDuPoste::contains)) {
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

            // Les exigences du poste, comparées à sa fiche.
            List<Map<String, Object>> etats = exigencesParAgent.getOrDefault(id, List.of());
            long remplies = etats.stream().filter(e -> "REMPLIE".equals(e.get("etat"))).count();
            for (Map<String, Object> etat : etats) {
                boolean indispensable = Boolean.TRUE.equals(etat.get("indispensable"));
                if ("REMPLIE".equals(etat.get("etat"))) {
                    score += indispensable ? 15 : 6;
                } else if ("MANQUANTE".equals(etat.get("etat")) && indispensable) {
                    score -= 25;
                    attentions.add("Exigence indispensable non remplie : " + etat.get("libelle"));
                }
            }
            if (!etats.isEmpty() && remplies == etats.size()) {
                raisons.add(0, etats.size() == 1 ? "Remplit l'exigence du poste"
                        : "Remplit les " + etats.size() + " exigences du poste");
            }
            candidat.put("exigences", etats);

            // Points d'attention : ils baissent le classement, ils n'excluent personne.
            LocalDate retraite = date(ligne.get("retraite_possible"));
            if (retraite != null && !retraite.isAfter(aujourdhui)) {
                score -= 50;
                attentions.add("Droit à la retraite déjà atteint (depuis le " + retraite.format(JOUR) + ")");
            } else if (retraite != null && !retraite.isAfter(liberation)) {
                score -= 50;
                attentions.add("Peut partir à la retraite le " + retraite.format(JOUR) + ", avant que le poste se libère");
            } else if (retraite != null && retraite.isBefore(liberation.plusYears(2))) {
                score -= 30;
                attentions.add("Part lui-même à la retraite le " + retraite.format(JOUR));
            }
            if (ligne.get("deja_choisi_pour") != null) {
                score -= 10;
                attentions.add("Déjà choisi pour le poste " + ligne.get("deja_choisi_pour"));
            }
            // Seulement pour un poste de responsable : ailleurs, presque tous les postes n'ont qu'une place
            // et l'avertissement, présent partout, n'aiderait plus à choisir.
            if (ligne.get("poste") != null && ligne.get("occupants_poste_actuel") instanceof Number occupants
                    && occupants.intValue() <= 1 && posteDeResponsable(ligne.get("poste"))) {
                score -= 5;
                attentions.add("Laisserait son poste actuel (" + ligne.get("poste") + ") sans titulaire");
            }

            candidat.put("score", score);
            candidat.put("raisons", raisons);
            candidat.put("attentions", attentions);
            for (String interne : List.of("categorie_min", "categorie_id", "deja_occupe_jusquau", "deja_occupe_depuis",
                    "occupants_poste_actuel", "poste_actuel_id")) {
                candidat.remove(interne);
            }
            classes.add(candidat);
        }
        classes.sort(java.util.Comparator.<Map<String, Object>>comparingInt(c -> -((Integer) c.get("score")))
                .thenComparing(c -> -(c.get("indice") instanceof Number indice ? indice.intValue() : 0))
                .thenComparing(c -> String.valueOf(c.get("nom"))));
        return classes;
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

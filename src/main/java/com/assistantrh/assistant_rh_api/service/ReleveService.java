package com.assistantrh.assistant_rh_api.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Le plan de relève : qui part dans les années qui viennent (retraite demandée
 * ou droit au départ atteint), quels postes se vident, et où les départs se
 * concentrent. Les départs viennent de PosteService (une seule source avec
 * l'organigramme).
 */
@Service
public class ReleveService {

    public static final List<String> STATUTS = List.of("A_PREPARER", "EN_PREPARATION", "PRET");

    private final PosteService posteService;
    private final com.assistantrh.assistant_rh_api.repository.PlanReleveRepository planRepository;

    public ReleveService(PosteService posteService,
                         com.assistantrh.assistant_rh_api.repository.PlanReleveRepository planRepository) {
        this.posteService = posteService;
        this.planRepository = planRepository;
    }

    /**
     * Désigne le successeur pressenti d'un poste (ou change sa préparation, sa note).
     * Une intention suivie : personne n'est muté.
     */
    public void designer(Integer posteId, Integer successeurId, String statut, String note, Integer acteurId) {
        if (posteId == null || successeurId == null) {
            throw new IllegalArgumentException("Choisissez le poste et son successeur.");
        }
        String etat = statut == null || statut.isBlank() ? "A_PREPARER" : statut.trim().toUpperCase(Locale.ROOT);
        if (!STATUTS.contains(etat)) {
            throw new IllegalArgumentException("La préparation est A_PREPARER, EN_PREPARATION ou PRET.");
        }
        String texte = note == null || note.isBlank() ? null : note.strip();
        if (texte != null && texte.length() > 500) {
            throw new IllegalArgumentException("La note ne peut pas dépasser 500 caractères.");
        }
        Map<String, Object> situation = planRepository.situationAgent(successeurId, posteId);
        if (!Boolean.TRUE.equals(situation.get("poste_existe"))) {
            throw new IllegalArgumentException("Le poste demandé n'existe pas.");
        }
        if (!Boolean.TRUE.equals(situation.get("actif"))) {
            throw new IllegalArgumentException("Le successeur doit être un agent en activité.");
        }
        if (Boolean.TRUE.equals(situation.get("titulaire"))) {
            throw new IllegalArgumentException("Cet agent occupe déjà ce poste : choisissez un autre successeur.");
        }
        planRepository.enregistrer(posteId, successeurId, etat, texte, acteurId);
    }

    public void retirer(Integer posteId) {
        if (posteId == null || !planRepository.supprimer(posteId)) {
            throw new IllegalArgumentException("Ce poste n'a pas de relève désignée.");
        }
    }

    /** Les postes à risque élevé, sur deux ans, qui n'ont encore aucun successeur désigné. */
    public int postesARisqueSansReleve() {
        Object nombre = ((Map<?, ?>) tableau(2).get("chiffres")).get("risque_eleve_sans_releve");
        return nombre instanceof Number n ? n.intValue() : 0;
    }

    public Map<String, Object> tableau(int horizonAnnees) {
        if (horizonAnnees < 1 || horizonAnnees > 10) {
            throw new IllegalArgumentException("L'horizon doit être compris entre 1 et 10 ans.");
        }
        LocalDate aujourdhui = LocalDate.now();
        LocalDate jusquau = aujourdhui.plusYears(horizonAnnees);
        LocalDate deuxAns = aujourdhui.plusYears(2);

        List<Map<String, Object>> departs = new ArrayList<>();
        List<Map<String, Object>> postesARisque = new ArrayList<>();
        Map<String, Map<String, Object>> services = new LinkedHashMap<>();
        Map<Object, Map<String, Object>> plans = new LinkedHashMap<>();
        planRepository.plans().forEach(plan -> plans.put(String.valueOf(plan.get("poste_id")), plan));

        for (Map<String, Object> poste : posteService.etatDesPostes(null, null, jusquau)) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> occupants = (List<Map<String, Object>>) poste.get("occupants");
            // Une demande de retraite au-delà de l'horizon n'y entre pas encore.
            List<Map<String, Object>> partants = occupants.stream()
                    .filter(occupant -> occupant.get("depart_prevu") instanceof LocalDate depart
                            && !depart.isAfter(jusquau)).toList();
            for (Map<String, Object> occupant : partants) {
                Map<String, Object> depart = new LinkedHashMap<>(occupant);
                LocalDate droit = (LocalDate) occupant.get("retraite_possible_le");
                depart.put("poste_id", poste.get("id"));
                depart.put("poste", poste.get("intitule"));
                depart.put("service_id", poste.get("service_id"));
                depart.put("service", poste.get("service"));
                depart.put("direction", poste.get("direction"));
                // Droit au départ déjà dépassé sans départ enregistré : à régulariser.
                depart.put("en_retard", "DROIT_RETRAITE".equals(occupant.get("motif_depart"))
                        && droit != null && droit.isBefore(aujourdhui));
                departs.add(depart);

                Map<String, Object> service = services.computeIfAbsent(String.valueOf(poste.get("service_id")), cle -> {
                    Map<String, Object> ligne = new LinkedHashMap<>();
                    ligne.put("service_id", poste.get("service_id"));
                    ligne.put("service", poste.get("service"));
                    ligne.put("direction", poste.get("direction"));
                    ligne.put("total", 0);
                    ligne.put("par_annee", new TreeMap<Integer, Integer>());
                    return ligne;
                });
                service.put("total", (Integer) service.get("total") + 1);
                @SuppressWarnings("unchecked")
                Map<Integer, Integer> parAnnee = (Map<Integer, Integer>) service.get("par_annee");
                parAnnee.merge(((LocalDate) occupant.get("depart_prevu")).getYear(), 1, Integer::sum);
            }
            if (partants.isEmpty()) {
                continue;
            }
            LocalDate premier = partants.stream().map(occupant -> (LocalDate) occupant.get("depart_prevu"))
                    .min(Comparator.naturalOrder()).orElseThrow();
            boolean vide = partants.size() >= occupants.size();
            boolean responsable = estResponsable(poste.get("intitule"));
            Map<String, Object> risque = new LinkedHashMap<>();
            risque.put("poste_id", poste.get("id"));
            risque.put("poste", poste.get("intitule"));
            risque.put("service_id", poste.get("service_id"));
            risque.put("service", poste.get("service"));
            risque.put("direction", poste.get("direction"));
            risque.put("places", poste.get("nombre_places"));
            risque.put("occupes", occupants.size());
            risque.put("partants", partants.size());
            risque.put("premier_depart", premier);
            risque.put("se_vide", vide);
            risque.put("responsable", responsable);
            // Élevé : le poste se vide dans les deux ans, ou c'est un poste de responsable.
            risque.put("niveau", (vide && !premier.isAfter(deuxAns)) || responsable ? "ELEVE" : "A_SURVEILLER");
            risque.put("releve", plans.get(String.valueOf(poste.get("id"))));
            risque.put("titulaires_partants", partants.stream().map(occupant -> Map.of(
                    "id", occupant.get("id"),
                    "nom", (texte(occupant.get("prenom")) + " " + texte(occupant.get("nom"))).strip(),
                    "depart_prevu", occupant.get("depart_prevu"))).toList());
            postesARisque.add(risque);
        }

        departs.sort(Comparator.comparing(depart -> (LocalDate) depart.get("depart_prevu")));
        postesARisque.sort(Comparator.<Map<String, Object>, Boolean>comparing(poste -> !"ELEVE".equals(poste.get("niveau")))
                .thenComparing(poste -> (LocalDate) poste.get("premier_depart")));
        List<Map<String, Object>> parService = new ArrayList<>(services.values());
        parService.sort(Comparator.comparing(service -> -((Integer) service.get("total"))));

        List<Integer> annees = new ArrayList<>();
        for (int annee = aujourdhui.getYear(); annee <= jusquau.getYear(); annee++) {
            annees.add(annee);
        }
        Map<String, Object> chiffres = new LinkedHashMap<>();
        chiffres.put("departs", departs.size());
        chiffres.put("postes_risque_eleve", postesARisque.stream().filter(p -> "ELEVE".equals(p.get("niveau"))).count());
        chiffres.put("postes_concernes", postesARisque.size());
        chiffres.put("services_touches", parService.size());
        chiffres.put("risque_eleve_sans_releve", postesARisque.stream()
                .filter(p -> "ELEVE".equals(p.get("niveau")) && p.get("releve") == null).count());

        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("horizon_annees", horizonAnnees);
        resultat.put("jusquau", jusquau);
        resultat.put("annees", annees);
        resultat.put("chiffres", chiffres);
        resultat.put("departs", departs);
        resultat.put("postes", postesARisque);
        resultat.put("services", parService);
        return resultat;
    }

    private static boolean estResponsable(Object intitule) {
        String texte = texte(intitule).toLowerCase(Locale.FRENCH);
        return texte.startsWith("chef") || texte.startsWith("directeur") || texte.startsWith("directrice")
                || texte.startsWith("responsable");
    }

    private static String texte(Object valeur) {
        return valeur == null ? "" : valeur.toString().strip();
    }
}

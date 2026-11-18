package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.ProfilComplementaireRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Situation familiale, langues, compétences et formations continues d'un agent :
 * lues d'un bloc, remplacées d'un bloc (le formulaire envoie tout ce qu'il montre).
 * Le Service du Personnel les modifie pour tout agent ; un agent, pour lui-même.
 */
@Service
public class ProfilComplementaireService {

    public static final List<String> SITUATIONS = List.of("CELIBATAIRE", "MARIE", "DIVORCE", "VEUF");
    public static final List<String> NIVEAUX = List.of("MATERNELLE", "COURANT", "INTERMEDIAIRE", "NOTIONS");
    private static final int MAX_ELEMENTS = 30;

    private final ProfilComplementaireRepository repository;
    private final EmployeRepository employeRepository;
    private final UtilisateurRepository utilisateurRepository;

    public ProfilComplementaireService(ProfilComplementaireRepository repository,
                                       EmployeRepository employeRepository,
                                       UtilisateurRepository utilisateurRepository) {
        this.repository = repository;
        this.employeRepository = employeRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    public Map<String, Object> lire(Integer employeId) {
        verifierAgent(employeId);
        Map<String, Object> famille = repository.situationFamiliale(employeId);
        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("employeId", employeId);
        resultat.put("situationMatrimoniale", famille.get("situation_matrimoniale"));
        resultat.put("nombreEnfants", famille.get("nombre_enfants"));
        resultat.put("langues", repository.langues(employeId));
        resultat.put("competences", repository.competences(employeId));
        resultat.put("formations", repository.formations(employeId).stream().map(ligne -> {
            Map<String, Object> formation = new LinkedHashMap<>();
            formation.put("intitule", ligne.get("intitule"));
            formation.put("organisme", ligne.get("organisme"));
            formation.put("annee", ligne.get("annee"));
            formation.put("dureeJours", ligne.get("duree_jours"));
            return formation;
        }).toList());
        return resultat;
    }

    public List<String> competencesConnues() {
        return repository.competencesConnues();
    }

    /** Le Service du Personnel modifie le profil complémentaire d'un agent. */
    @Transactional
    public Map<String, Object> enregistrerPourAgent(Integer employeId, Integer acteurId, Map<String, Object> corps) {
        if (acteurId == null || !utilisateurRepository.estAgentSpersActif(acteurId)) {
            throw new IllegalArgumentException("Seul un agent actif du Service du Personnel peut modifier ce profil.");
        }
        return enregistrer(employeId, corps);
    }

    /** Un agent modifie son propre profil complémentaire. */
    @Transactional
    public Map<String, Object> enregistrerPourSoi(Integer utilisateurId, Map<String, Object> corps) {
        return enregistrer(employeDe(utilisateurId), corps);
    }

    public Integer employeDe(Integer utilisateurId) {
        return employeRepository.findByUserId(utilisateurId)
                .map(agent -> ((Number) agent.get("id")).intValue())
                .orElseThrow(() -> new IllegalArgumentException("Aucun agent n'est associé à ce compte."));
    }

    private Map<String, Object> enregistrer(Integer employeId, Map<String, Object> corps) {
        verifierAgent(employeId);
        Map<String, Object> donnees = corps == null ? Map.of() : corps;
        String situation = texte(donnees.get("situationMatrimoniale"), 20, "La situation matrimoniale");
        if (situation != null && !SITUATIONS.contains(situation)) {
            throw new IllegalArgumentException("La situation matrimoniale est invalide.");
        }
        Integer enfants = entier(donnees.get("nombreEnfants"), "Le nombre d'enfants");
        if (enfants != null && (enfants < 0 || enfants > 30)) {
            throw new IllegalArgumentException("Le nombre d'enfants doit être compris entre 0 et 30.");
        }
        List<Map<String, Object>> langues = langues(donnees.get("langues"));
        List<String> competences = competences(donnees.get("competences"));
        List<Map<String, Object>> formations = formations(donnees.get("formations"));

        repository.enregistrerSituationFamiliale(employeId, situation, enfants);
        repository.remplacerListes(employeId, langues, competences, formations);
        return lire(employeId);
    }

    private List<Map<String, Object>> langues(Object valeur) {
        List<Map<String, Object>> resultat = new ArrayList<>();
        Set<String> vues = new HashSet<>();
        for (Map<String, Object> element : elements(valeur, "langues")) {
            String langue = texte(element.get("langue"), 40, "Le nom d'une langue");
            if (langue == null) {
                continue;
            }
            String niveau = texte(element.get("niveau"), 20, "Le niveau d'une langue");
            if (niveau == null || !NIVEAUX.contains(niveau)) {
                throw new IllegalArgumentException("Choisissez le niveau de la langue « " + langue + " ».");
            }
            if (!vues.add(langue.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("La langue « " + langue + " » figure deux fois.");
            }
            resultat.add(Map.of("langue", langue, "niveau", niveau));
        }
        return resultat;
    }

    private List<String> competences(Object valeur) {
        if (valeur == null) {
            return List.of();
        }
        if (!(valeur instanceof List<?> liste)) {
            throw new IllegalArgumentException("Les compétences doivent être une liste.");
        }
        if (liste.size() > MAX_ELEMENTS) {
            throw new IllegalArgumentException("Au plus " + MAX_ELEMENTS + " compétences.");
        }
        List<String> resultat = new ArrayList<>();
        Set<String> vues = new HashSet<>();
        for (Object element : liste) {
            String competence = texte(element, 80, "Une compétence");
            if (competence != null && vues.add(competence.toLowerCase(Locale.ROOT))) {
                resultat.add(competence);
            }
        }
        return resultat;
    }

    private List<Map<String, Object>> formations(Object valeur) {
        List<Map<String, Object>> resultat = new ArrayList<>();
        int anneeMax = LocalDate.now().getYear();
        for (Map<String, Object> element : elements(valeur, "formations")) {
            String intitule = texte(element.get("intitule"), 200, "L'intitulé d'une formation");
            if (intitule == null) {
                continue;
            }
            Integer annee = entier(element.get("annee"), "L'année de la formation « " + intitule + " »");
            if (annee != null && (annee < 1950 || annee > anneeMax)) {
                throw new IllegalArgumentException("L'année de la formation « " + intitule
                        + " » doit être comprise entre 1950 et " + anneeMax + ".");
            }
            Integer duree = entier(element.get("dureeJours"), "La durée de la formation « " + intitule + " »");
            if (duree != null && (duree < 1 || duree > 999)) {
                throw new IllegalArgumentException("La durée de la formation « " + intitule
                        + " » doit être comprise entre 1 et 999 jours.");
            }
            Map<String, Object> formation = new LinkedHashMap<>();
            formation.put("intitule", intitule);
            formation.put("organisme", texte(element.get("organisme"), 150, "L'organisme d'une formation"));
            formation.put("annee", annee);
            formation.put("dureeJours", duree);
            resultat.add(formation);
        }
        return resultat;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> elements(Object valeur, String nom) {
        if (valeur == null) {
            return List.of();
        }
        if (!(valeur instanceof List<?> liste)) {
            throw new IllegalArgumentException("Les " + nom + " doivent être une liste.");
        }
        if (liste.size() > MAX_ELEMENTS) {
            throw new IllegalArgumentException("Au plus " + MAX_ELEMENTS + " " + nom + ".");
        }
        List<Map<String, Object>> resultat = new ArrayList<>();
        for (Object element : liste) {
            if (!(element instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Un élément des " + nom + " est invalide.");
            }
            resultat.add((Map<String, Object>) map);
        }
        return resultat;
    }

    private void verifierAgent(Integer employeId) {
        if (employeId == null || employeId <= 0 || employeRepository.findById(employeId).isEmpty()) {
            throw new IllegalArgumentException("L'agent demandé n'existe pas.");
        }
    }

    private static String texte(Object valeur, int longueurMax, String libelle) {
        if (valeur == null || valeur.toString().isBlank()) {
            return null;
        }
        String propre = valeur.toString().strip().replaceAll("\\s+", " ");
        if (propre.length() > longueurMax) {
            throw new IllegalArgumentException(libelle + " ne peut pas dépasser " + longueurMax + " caractères.");
        }
        if (propre.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(libelle + " contient des caractères non autorisés.");
        }
        return propre;
    }

    private static Integer entier(Object valeur, String libelle) {
        if (valeur == null || valeur.toString().isBlank()) {
            return null;
        }
        if (valeur instanceof Number nombre) {
            return nombre.intValue();
        }
        try {
            return Integer.valueOf(valeur.toString().strip());
        } catch (NumberFormatException erreur) {
            throw new IllegalArgumentException(libelle + " doit être un nombre entier.");
        }
    }
}

package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.PosteExigenceRepository;
import com.assistantrh.assistant_rh_api.repository.PosteRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Les exigences d'un poste (langue, diplôme, compétence, formation) et leur
 * comparaison au profil d'un agent. Trois réponses possibles pour chaque
 * exigence : REMPLIE, MANQUANTE, ou A_VERIFIER quand la fiche de l'agent ne dit
 * rien (on ne conclut jamais qu'il ne sait pas ce qui n'est pas renseigné).
 */
@Service
public class ExigencePosteService {

    public static final List<String> TYPES = List.of("LANGUE", "DIPLOME", "COMPETENCE", "FORMATION");
    /** Du plus faible au plus fort : le niveau minimum est rempli par tout niveau au-dessus. */
    public static final List<String> NIVEAUX_LANGUE = List.of("NOTIONS", "INTERMEDIAIRE", "COURANT", "MATERNELLE");
    private static final Map<String, String> LIBELLES_NIVEAU_LANGUE = Map.of(
            "NOTIONS", "notions", "INTERMEDIAIRE", "intermédiaire", "COURANT", "courant", "MATERNELLE", "langue maternelle");

    private final PosteExigenceRepository exigenceRepository;
    private final PosteRepository posteRepository;

    public ExigencePosteService(PosteExigenceRepository exigenceRepository, PosteRepository posteRepository) {
        this.exigenceRepository = exigenceRepository;
        this.posteRepository = posteRepository;
    }

    public List<Map<String, Object>> lister(Long posteId) {
        verifierPoste(posteId);
        return exigenceRepository.exigences(posteId).stream().map(exigence -> {
            Map<String, Object> ligne = new LinkedHashMap<>(exigence);
            ligne.put("libelle", libelle(exigence));
            ligne.remove("niveau_ordre");
            return ligne;
        }).toList();
    }

    /** Remplace les exigences du poste, après vérification de chacune. */
    public List<Map<String, Object>> enregistrer(Long posteId, List<Map<String, Object>> exigences) {
        verifierPoste(posteId);
        if (exigences == null) {
            exigences = List.of();
        }
        if (exigences.size() > 12) {
            throw new IllegalArgumentException("Un poste a au plus 12 exigences : gardez l'essentiel.");
        }
        List<Map<String, Object>> propres = new ArrayList<>();
        for (Map<String, Object> brute : exigences) {
            String type = texte(brute.get("type")).toUpperCase(Locale.ROOT);
            String valeur = texte(brute.get("valeur"));
            String niveau = texte(brute.get("niveau")).toUpperCase(Locale.ROOT);
            if (!TYPES.contains(type)) {
                throw new IllegalArgumentException("Le type d'exigence est LANGUE, DIPLOME, COMPETENCE ou FORMATION.");
            }
            if (valeur.length() > 150) {
                throw new IllegalArgumentException("Une exigence tient en 150 caractères au plus.");
            }
            switch (type) {
                case "LANGUE" -> {
                    if (valeur.isEmpty()) throw new IllegalArgumentException("Indiquez la langue demandée.");
                    if (!niveau.isEmpty() && !NIVEAUX_LANGUE.contains(niveau)) {
                        throw new IllegalArgumentException("Le niveau de langue est NOTIONS, INTERMEDIAIRE, COURANT ou MATERNELLE.");
                    }
                }
                case "DIPLOME" -> {
                    if (niveau.isEmpty() || !exigenceRepository.niveauDiplomeExiste(niveau)) {
                        throw new IllegalArgumentException("Choisissez le niveau de diplôme demandé.");
                    }
                }
                default -> {
                    if (valeur.isEmpty()) {
                        throw new IllegalArgumentException("COMPETENCE".equals(type)
                                ? "Indiquez la compétence demandée." : "Indiquez la formation demandée.");
                    }
                    niveau = "";
                }
            }
            Map<String, Object> propre = new HashMap<>();
            propre.put("type", type);
            propre.put("valeur", valeur.isEmpty() ? null : valeur);
            propre.put("niveau", niveau.isEmpty() ? null : niveau);
            propre.put("indispensable", Boolean.TRUE.equals(brute.get("indispensable")));
            propres.add(propre);
        }
        exigenceRepository.remplacer(posteId, propres);
        return lister(posteId);
    }

    /**
     * Pour chaque agent, l'état de chaque exigence du poste :
     * { libelle, type, indispensable, etat (REMPLIE, MANQUANTE, A_VERIFIER), detail }.
     */
    public Map<Integer, List<Map<String, Object>>> evaluer(Long posteId, List<Integer> agentIds) {
        List<Map<String, Object>> exigences = exigenceRepository.exigences(posteId);
        Map<Integer, List<Map<String, Object>>> resultat = new HashMap<>();
        if (exigences.isEmpty() || agentIds.isEmpty()) {
            return resultat;
        }
        Map<Integer, List<Map<String, Object>>> langues = parAgent(exigenceRepository.langues(agentIds));
        Map<Integer, List<Map<String, Object>>> diplomes = parAgent(posteRepository.diplomes(agentIds));
        Map<Integer, List<Map<String, Object>>> competences = parAgent(posteRepository.competences(agentIds));
        Map<Integer, List<Map<String, Object>>> formations = parAgent(posteRepository.formations(agentIds));
        for (Integer agent : agentIds) {
            List<Map<String, Object>> etats = new ArrayList<>();
            for (Map<String, Object> exigence : exigences) {
                Map<String, Object> etat = switch (String.valueOf(exigence.get("type"))) {
                    case "LANGUE" -> langue(exigence, langues.getOrDefault(agent, List.of()));
                    case "DIPLOME" -> diplome(exigence, diplomes.getOrDefault(agent, List.of()));
                    case "COMPETENCE" -> mot(exigence, competences.getOrDefault(agent, List.of()), "libelle",
                            "Compétences non renseignées sur sa fiche");
                    default -> mot(exigence, formations.getOrDefault(agent, List.of()), "intitule",
                            "Formations non renseignées sur sa fiche");
                };
                etat.put("libelle", libelle(exigence));
                etat.put("type", exigence.get("type"));
                etat.put("indispensable", Boolean.TRUE.equals(exigence.get("indispensable")));
                etats.add(etat);
            }
            resultat.put(agent, etats);
        }
        return resultat;
    }

    /** « Anglais (courant minimum) », « Licence minimum en Droit », « Compétence : Gestion de la paie ». */
    public static String libelle(Map<String, Object> exigence) {
        String valeur = texte(exigence.get("valeur"));
        String niveau = texte(exigence.get("niveau"));
        return switch (String.valueOf(exigence.get("type"))) {
            case "LANGUE" -> valeur + (niveau.isEmpty() ? "" : " (" + LIBELLES_NIVEAU_LANGUE.getOrDefault(niveau, niveau)
                    + ("MATERNELLE".equals(niveau) ? ")" : " minimum)"));
            case "DIPLOME" -> texte(exigence.get("niveau_libelle")).replaceFirst("^$", niveau) + " minimum"
                    + (valeur.isEmpty() ? "" : " en " + valeur);
            case "COMPETENCE" -> "Compétence : " + valeur;
            default -> "Formation : " + valeur;
        };
    }

    private Map<String, Object> langue(Map<String, Object> exigence, List<Map<String, Object>> langues) {
        if (langues.isEmpty()) {
            return etat("A_VERIFIER", "Langues non renseignées sur sa fiche");
        }
        String voulue = normaliser(exigence.get("valeur"));
        int minimum = NIVEAUX_LANGUE.indexOf(texte(exigence.get("niveau")));
        for (Map<String, Object> langue : langues) {
            if (normaliser(langue.get("langue")).equals(voulue)) {
                String niveau = texte(langue.get("niveau"));
                String detail = langue.get("langue") + " : " + LIBELLES_NIVEAU_LANGUE.getOrDefault(niveau, niveau.toLowerCase(Locale.ROOT));
                return NIVEAUX_LANGUE.indexOf(niveau) >= minimum
                        ? etat("REMPLIE", detail) : etat("MANQUANTE", detail + " seulement");
            }
        }
        return etat("MANQUANTE", "Langues de sa fiche : " + String.join(", ",
                langues.stream().map(l -> texte(l.get("langue"))).toList()));
    }

    private Map<String, Object> diplome(Map<String, Object> exigence, List<Map<String, Object>> diplomes) {
        if (diplomes.isEmpty()) {
            return etat("A_VERIFIER", "Aucun diplôme renseigné sur sa fiche");
        }
        int minimum = exigence.get("niveau_ordre") instanceof Number ordre ? ordre.intValue() : 0;
        String domaine = normaliser(exigence.get("valeur"));
        Map<String, Object> meilleur = null;
        for (Map<String, Object> diplome : diplomes) {
            int ordre = diplome.get("ordre") instanceof Number n ? n.intValue() : 0;
            boolean bonDomaine = domaine.isEmpty() || normaliser(diplome.get("domaine")).equals(domaine);
            if (ordre >= minimum && bonDomaine) {
                return etat("REMPLIE", decrire(diplome));
            }
            if (meilleur == null || ordre > (meilleur.get("ordre") instanceof Number m ? m.intValue() : 0)) {
                meilleur = diplome;
            }
        }
        return etat("MANQUANTE", "Plus haut diplôme : " + decrire(meilleur));
    }

    private Map<String, Object> mot(Map<String, Object> exigence, List<Map<String, Object>> elements, String champ,
                                    String nonRenseigne) {
        if (elements.isEmpty()) {
            return etat("A_VERIFIER", nonRenseigne);
        }
        String voulu = normaliser(exigence.get("valeur"));
        for (Map<String, Object> element : elements) {
            String present = normaliser(element.get(champ));
            if (!present.isEmpty() && (present.contains(voulu) || voulu.contains(present))) {
                return etat("REMPLIE", texte(element.get(champ)));
            }
        }
        return etat("MANQUANTE", "Pas sur sa fiche");
    }

    private static String decrire(Map<String, Object> diplome) {
        if (diplome == null) {
            return "";
        }
        String domaine = texte(diplome.get("domaine"));
        return texte(diplome.get("niveau")) + (domaine.isEmpty() ? "" : " en " + domaine);
    }

    private static Map<String, Object> etat(String etat, String detail) {
        Map<String, Object> resultat = new LinkedHashMap<>();
        resultat.put("etat", etat);
        resultat.put("detail", detail);
        return resultat;
    }

    private static Map<Integer, List<Map<String, Object>>> parAgent(List<Map<String, Object>> lignes) {
        Map<Integer, List<Map<String, Object>>> parAgent = new HashMap<>();
        for (Map<String, Object> ligne : lignes) {
            parAgent.computeIfAbsent(((Number) ligne.get("employe_id")).intValue(), cle -> new ArrayList<>()).add(ligne);
        }
        return parAgent;
    }

    private void verifierPoste(Long posteId) {
        if (posteId == null || posteRepository.findById(posteId) == null) {
            throw new IllegalArgumentException("Le poste demandé n'existe pas.");
        }
    }

    /** Sans accents, en minuscules : « Français » et « francais » se valent. */
    static String normaliser(Object valeur) {
        return Normalizer.normalize(texte(valeur), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    private static String texte(Object valeur) {
        return Objects.toString(valeur, "").strip();
    }
}

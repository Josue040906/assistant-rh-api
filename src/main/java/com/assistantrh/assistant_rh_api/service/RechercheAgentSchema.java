package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.SituationCarriereRepository;

import java.util.Map;
import java.util.TreeMap;

/**
 * Tout ce que la recherche d'agents sait interroger, déclaré une seule fois :
 * les champs de l'agent, puis ses listes (diplômes, carrière, affectations…),
 * chacune avec ses propres champs. Ajouter une information cherchable, c'est
 * ajouter une ligne ici ; le moteur, la validation et les messages d'erreur
 * en découlent.
 */
final class RechercheAgentSchema {

    /** Le type d'un champ décide des opérateurs permis et de la comparaison. */
    enum Type {
        /** Texte comparé sans casse ni accents ; FUZZY autorisé. */
        TEXTE,
        /** Nom et prénom réunis ; FUZZY_PERSON en plus du texte. */
        PERSONNE,
        /** Code comparé en majuscules ; FUZZY autorisé. */
        CODE,
        /** Identifiant exact (matricule, CIN) : jamais approché. */
        IDENTIFIANT,
        /** F ou M. */
        SEXE,
        NOMBRE,
        DATE,
        /** Référentiel ordonné : comparé par son libellé (« Licence », « Principale »). */
        RANG
    }

    /**
     * Champ : son SQL brut et son type. Un rang a en plus la requête de ses
     * valeurs (libelle, code, rang) et le SQL de son libellé, pour l'afficher.
     */
    record Champ(String sql, Type type, String valeursSql, String libelleSql) {
        static Champ de(String sql, Type type) {
            return new Champ(sql, type, null, null);
        }

        static Champ rang(String sql, String valeursSql, String libelleSql) {
            return new Champ(sql, Type.RANG, valeursSql, libelleSql);
        }
    }

    /**
     * Une liste rattachée à l'agent, interrogée par EXISTS. resume dit en clair
     * un élément de la liste : c'est lui qui explique pourquoi un agent a été
     * retenu (« Licence Informatique (Université d'Antananarivo, 2018) »).
     */
    record Liste(String from, String lien, String resume, Map<String, Champ> champs) {
    }

    private static final String NIVEAUX = """
            SELECT libelle, code, ordre_academique AS rang FROM niveau_diplome
            WHERE ordre_academique IS NOT NULL ORDER BY rang""";
    private static final String CLASSES =
            "SELECT libelle, NULL AS code, ordre AS rang FROM classe ORDER BY rang";

    static final Map<String, Champ> AGENT = new TreeMap<>(Map.ofEntries(
            Map.entry("matricule", Champ.de("e.matricule", Type.IDENTIFIANT)),
            Map.entry("cin", Champ.de("e.cin", Type.IDENTIFIANT)),
            Map.entry("nom", Champ.de("e.nom", Type.TEXTE)),
            Map.entry("prenom", Champ.de("e.prenom", Type.TEXTE)),
            Map.entry("personne", Champ.de("concat_ws(' ', e.nom, e.prenom)", Type.PERSONNE)),
            Map.entry("nom_complet", Champ.de("concat_ws(' ', e.nom, e.prenom)", Type.PERSONNE)),
            Map.entry("sexe", Champ.de("e.sexe", Type.SEXE)),
            Map.entry("adresse", Champ.de("e.adresse", Type.TEXTE)),
            Map.entry("telephone", Champ.de("e.telephone", Type.TEXTE)),
            Map.entry("lieu_naissance", Champ.de("e.lieu_naissance", Type.TEXTE)),
            Map.entry("lieu_travail", Champ.de("e.lieu_travail", Type.TEXTE)),
            Map.entry("residence", Champ.de("lr.nom", Type.TEXTE)),
            Map.entry("poste", Champ.de("p.intitule", Type.TEXTE)),
            Map.entry("service", Champ.de("concat_ws(' ', s.code, s.nom)", Type.TEXTE)),
            Map.entry("direction", Champ.de("d.nom", Type.TEXTE)),
            Map.entry("categorie", Champ.de("c.code", Type.CODE)),
            Map.entry("corps", Champ.de("concat_ws(' ', co.code, co.libelle)", Type.TEXTE)),
            Map.entry("type_emploi", Champ.de("te.nom", Type.TEXTE)),
            Map.entry("classe", Champ.rang("cl.ordre", CLASSES, "cl.libelle")),
            Map.entry("echelon", Champ.de("ec.ordre", Type.NOMBRE)),
            Map.entry("grade", Champ.de("concat_ws(' ', ec.code, ec.libelle)", Type.TEXTE)),
            Map.entry("indice", Champ.de("(SELECT gi.indice FROM grille_indiciaire gi WHERE gi.categorie_id = hc.categorie_id AND gi.echelon_id = hc.echelon_id)", Type.NOMBRE)),
            Map.entry("parcours", Champ.de(
                    "(SELECT pa.libelle FROM parcours pa WHERE pa.id = cl.parcours_id)", Type.TEXTE)),
            Map.entry("age", Champ.de(
                    "EXTRACT(YEAR FROM age(CURRENT_DATE, e.date_naissance))::INTEGER", Type.NOMBRE)),
            Map.entry("anciennete_echelon", Champ.de(
                    "EXTRACT(YEAR FROM age(CURRENT_DATE, hc.date_debut))::INTEGER", Type.NOMBRE)),
            Map.entry("date_naissance", Champ.de("e.date_naissance", Type.DATE)),
            Map.entry("date_embauche", Champ.de("e.date_embauche", Type.DATE)),
            Map.entry("date_anciennete_echelon", Champ.de(
                    SituationCarriereRepository.DATE_ANCIENNETE_ECHELON, Type.DATE))
    ));

    static final Map<String, Liste> LISTES = new TreeMap<>(Map.of(
            "diplomes", new Liste("""
                    employe_diplome ed
                    JOIN niveau_diplome n ON n.id = ed.niveau_id
                    JOIN domaine_etude dom ON dom.id = ed.domaine_id
                    JOIN etablissement et ON et.id = ed.etablissement_id
                    LEFT JOIN mention m ON m.id = ed.mention_id""",
                    "ed.employe_id = e.id",
                    "concat_ws(' ', n.libelle, dom.libelle) || ' (' || concat_ws(', ', et.nom, "
                            + "EXTRACT(YEAR FROM ed.date_obtention)::INTEGER, m.libelle) || ')'",
                    new TreeMap<>(Map.of(
                            "niveau", Champ.rang("n.ordre_academique", NIVEAUX, "n.libelle"),
                            "domaine", Champ.de("dom.libelle", Type.TEXTE),
                            "etablissement", Champ.de("et.nom", Type.TEXTE),
                            "ville", Champ.de("et.ville", Type.TEXTE),
                            "mention", Champ.de("m.libelle", Type.TEXTE),
                            "date_obtention", Champ.de("ed.date_obtention", Type.DATE)
                    ))),
            "carriere", new Liste("""
                    historique_carriere h
                    JOIN echelon hec ON hec.id = h.echelon_id
                    JOIN classe hcl ON hcl.id = hec.classe_id
                    LEFT JOIN categorie hca ON hca.id = h.categorie_id""",
                    "h.employe_id = e.id",
                    "concat_ws(', ', 'catégorie ' || hca.code, hec.libelle || ' (' || hec.code || ')') "
                            + "|| ' (du ' || to_char(h.date_debut, 'DD/MM/YYYY') "
                            + "|| coalesce(' au ' || to_char(h.date_fin, 'DD/MM/YYYY'), ', en cours') || ')'",
                    new TreeMap<>(Map.of(
                            "categorie", Champ.de("hca.code", Type.CODE),
                            "classe", Champ.rang("hcl.ordre", CLASSES, "hcl.libelle"),
                            "echelon", Champ.de("hec.ordre", Type.NOMBRE),
                            "grade", Champ.de("concat_ws(' ', hec.code, hec.libelle)", Type.TEXTE),
                            "indice", Champ.de("(SELECT gi.indice FROM grille_indiciaire gi "
                                    + "WHERE gi.categorie_id = h.categorie_id AND gi.echelon_id = h.echelon_id)",
                                    Type.NOMBRE),
                            "date_debut", Champ.de("h.date_debut", Type.DATE),
                            "date_fin", Champ.de("h.date_fin", Type.DATE)
                    ))),
            "affectations", new Liste("""
                    affectation a
                    LEFT JOIN poste ap ON ap.id = a.poste_id
                    LEFT JOIN service asv ON asv.id = a.service_id
                    LEFT JOIN direction ad ON ad.id = asv.direction_id""",
                    "a.employe_id = e.id",
                    "concat_ws(' · ', ap.intitule, asv.nom, ad.nom) "
                            + "|| ' (du ' || to_char(a.date_debut, 'DD/MM/YYYY') "
                            + "|| coalesce(' au ' || to_char(a.date_fin, 'DD/MM/YYYY'), ', en cours') || ')'",
                    new TreeMap<>(Map.of(
                            "poste", Champ.de("ap.intitule", Type.TEXTE),
                            "service", Champ.de("concat_ws(' ', asv.code, asv.nom)", Type.TEXTE),
                            "direction", Champ.de("ad.nom", Type.TEXTE),
                            "date_debut", Champ.de("a.date_debut", Type.DATE),
                            "date_fin", Champ.de("a.date_fin", Type.DATE)
                    ))),
            "avancements", new Liste("avancement av", "av.employe_id = e.id",
                    "concat_ws(', ', av.type_avancement, av.statut, 'effet le ' || to_char(av.date_effet, 'DD/MM/YYYY'))",
                    new TreeMap<>(Map.of(
                            "type", Champ.de("av.type_avancement", Type.TEXTE),
                            "statut", Champ.de("av.statut", Type.CODE),
                            "date_demande", Champ.de("av.date_demande", Type.DATE),
                            "date_decision", Champ.de("av.date_decision", Type.DATE),
                            "date_effet", Champ.de("av.date_effet", Type.DATE)
                    ))),
            "documents", new Liste("""
                    document doc
                    JOIN type_document td ON td.id = doc.type_document_id""",
                    "doc.employe_id = e.id",
                    "concat_ws(', ', td.libelle, doc.statut, 'du ' || to_char(doc.date_document, 'DD/MM/YYYY'))",
                    new TreeMap<>(Map.of(
                            "type", Champ.de("td.libelle", Type.TEXTE),
                            "statut", Champ.de("doc.statut", Type.CODE),
                            "objet", Champ.de("doc.objet", Type.TEXTE),
                            "date_document", Champ.de("doc.date_document", Type.DATE),
                            "date_effet", Champ.de("doc.date_effet", Type.DATE)
                    )))
    ));

    private RechercheAgentSchema() {
    }
}

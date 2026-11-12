package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.AFaireRepository;
import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Le point du jour : ce qui demande une action de l'utilisateur connecté, du
 * plus urgent au moins urgent, puis ce qui arrive (anciennetés du mois, départs
 * à la retraite de l'année, congés à poser, postes qui se libèrent). Chaque élément dit quoi, combien et où agir : le front
 * l'affiche tel quel et Bandy le présente, sans connaître les règles.
 * Un élément à zéro n'est pas renvoyé.
 */
@Service
public class AFaireService {
    /** Mois à partir duquel le point du jour rappelle les congés à poser avant le 1er janvier. */
    private static final int MOIS_RAPPEL_CONGES = 10;

    private final AFaireRepository aFaireRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DocumentRhRepository documentRhRepository;
    private final PosteService posteService;

    public AFaireService(
            AFaireRepository aFaireRepository,
            UtilisateurRepository utilisateurRepository,
            DocumentRhRepository documentRhRepository,
            PosteService posteService
    ) {
        this.aFaireRepository = aFaireRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.documentRhRepository = documentRhRepository;
        this.posteService = posteService;
    }

    public AFaire pour(int utilisateurId) {
        List<Element> elements = new ArrayList<>();
        if (utilisateurRepository.estChefActif(utilisateurId)) {
            ajouter(elements, "comptes_en_attente", "Demandes d'inscription en attente",
                    aFaireRepository.comptesEnAttente(), "/administration/demandes-inscription");
        }
        ajouter(elements, "demandes_sans_reponse", "Demandes dont la réponse signée n'est pas saisie",
                aFaireRepository.demandesSansReponse(), "/documents?statut=CREE");
        ajouter(elements, "demandes_applicables", "Demandes acceptées à appliquer",
                aFaireRepository.demandesApplicables(), "/documents?statut=APPLICABLE");
        ajouter(elements, "dossiers_incomplets", "Dossiers d'agents incomplets",
                aFaireRepository.dossiersIncomplets(), "/agents?dossier=incomplet");

        // Ce qui arrive : chaque élément ouvre la liste des agents concernés.
        LocalDate aujourdhui = LocalDate.now();
        LocalDate debutMois = aujourdhui.withDayOfMonth(1);
        LocalDate finMois = debutMois.plusMonths(1).minusDays(1);
        ajouter(elements, "anciennetes_du_mois", "Agents qui atteignent leur ancienneté d'échelon ce mois-ci",
                aFaireRepository.ancienneteAtteinteEntre(debutMois, finMois),
                recherche("date_anciennete_echelon", "BETWEEN", debutMois, finMois));
        documentRhRepository.ancienneteRetraiteAnnees().ifPresent(annees -> {
            // Droit à la retraite dans les douze mois = embauché il y a « annees » ans, à douze mois près.
            LocalDate du = aujourdhui.minusYears(annees);
            LocalDate au = aujourdhui.plusMonths(12).minusYears(annees);
            ajouter(elements, "retraites_de_l_annee", "Départs à la retraite possibles dans les 12 prochains mois",
                    aFaireRepository.embauchesEntre(du, au), recherche("date_embauche", "BETWEEN", du, au));
        });
        if (aujourdhui.getMonthValue() >= MOIS_RAPPEL_CONGES) {
            ajouter(elements, "conges_a_poser", "Agents qui ont encore des congés à poser avant le 31/12",
                    aFaireRepository.agentsAvecSoldeConge(),
                    "/agents?recherche=" + encoder("{\"must\":[{\"field\":\"solde_conge\",\"operator\":\"GT\",\"value\":0}]}"));
        }
        ajouter(elements, "postes_se_liberent", "Postes qui se libèrent dans l'année",
                (int) posteService.etatDesPostes(null, null).stream()
                        .filter(poste -> "SE_LIBERE".equals(poste.get("statut"))).count(),
                "/organisation");
        return new AFaire(elements);
    }

    /** La page Agents filtrée sur une recherche (un critère de date, entre deux jours). */
    private static String recherche(String champ, String operateur, LocalDate du, LocalDate au) {
        return "/agents?recherche=" + encoder("{\"must\":[{\"field\":\"" + champ + "\",\"operator\":\""
                + operateur + "\",\"value\":\"" + du + "\",\"upperValue\":\"" + au + "\"}]}");
    }

    private static String encoder(String texte) {
        return URLEncoder.encode(texte, StandardCharsets.UTF_8);
    }

    private static void ajouter(
            List<Element> elements, String code, String libelle, int nombre, String route
    ) {
        if (nombre > 0) {
            elements.add(new Element(code, libelle, nombre, route));
        }
    }

    public record AFaire(List<Element> elements) {
    }

    public record Element(String code, String libelle, int nombre, String route) {
    }
}

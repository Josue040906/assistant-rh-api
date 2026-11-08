package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.AFaireRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Ce qui demande une action de l'utilisateur connecté, du plus urgent au
 * moins urgent. Chaque élément dit quoi, combien et où agir : le front
 * l'affiche tel quel et Bandy le présente, sans connaître les règles.
 * Un élément à zéro n'est pas renvoyé.
 */
@Service
public class AFaireService {
    private final AFaireRepository aFaireRepository;
    private final UtilisateurRepository utilisateurRepository;

    public AFaireService(
            AFaireRepository aFaireRepository,
            UtilisateurRepository utilisateurRepository
    ) {
        this.aFaireRepository = aFaireRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    public AFaire pour(int utilisateurId) {
        List<Element> elements = new ArrayList<>();
        if (utilisateurRepository.estChefActif(utilisateurId)) {
            ajouter(elements, "comptes_en_attente", "Demandes d'inscription en attente",
                    aFaireRepository.comptesEnAttente(), "/administration/demandes-inscription");
        }
        ajouter(elements, "documents_a_verifier", "Documents à vérifier",
                aFaireRepository.documentsAuStatut("A_VERIFIER"), "/documents?statut=A_VERIFIER");
        ajouter(elements, "documents_brouillon", "Documents en brouillon",
                aFaireRepository.documentsAuStatut("BROUILLON"), "/documents?statut=BROUILLON");
        ajouter(elements, "dossiers_incomplets", "Dossiers d'agents incomplets",
                aFaireRepository.dossiersIncomplets(), "/agents?dossier=incomplet");
        return new AFaire(elements);
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

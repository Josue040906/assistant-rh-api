package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import com.assistantrh.assistant_rh_api.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Relie une action directe (avancement, mutation, retraite) aux demandes du
 * même agent qu'elle satisfait : elles passent à « Appliqué », leur auteur et
 * le chef en sont prévenus. Une demande appliquée par sa propre fiche passe
 * par la même action directe, mais elle est déjà appliquée : rien n'est compté deux fois.
 */
@Service
public class DemandesLieesService {
    private static final String CIBLE = "d.donnees -> 'situationDemandee'";

    private final DocumentRhRepository documentRhRepository;
    private final NotificationRepository notificationRepository;
    private final ActiviteService activiteService;

    public DemandesLieesService(
            DocumentRhRepository documentRhRepository,
            NotificationRepository notificationRepository,
            ActiviteService activiteService
    ) {
        this.documentRhRepository = documentRhRepository;
        this.notificationRepository = notificationRepository;
        this.activiteService = activiteService;
    }

    /** Les demandes d'avancement vers ce même grade. */
    public void avancement(Integer employeId, Integer acteurId, Integer echelonId) {
        signaler(acteurId, documentRhRepository.appliquerDemandesSatisfaites(
                employeId, "AVANCEMENT", "(" + CIBLE + " ->> 'echelonId')::int = ?", echelonId));
    }

    /** Les demandes de mutation vers ce poste, ce service (sans poste précis) ou ce seul lieu. */
    public void mutation(Integer employeId, Integer acteurId, Integer posteId, Integer serviceId, String lieu) {
        String poste = "(" + CIBLE + " ->> 'posteId')";
        String service = "(" + CIBLE + " ->> 'serviceId')";
        String lieuDemande = "(" + CIBLE + " ->> 'lieuTravail')";
        signaler(acteurId, documentRhRepository.appliquerDemandesSatisfaites(
                employeId, "MUTATION",
                poste + "::int = ? OR (" + poste + " IS NULL AND " + service + "::int = ?)"
                        + " OR (" + poste + " IS NULL AND " + service + " IS NULL AND " + lieuDemande + " = ?)",
                posteId, serviceId, lieu));
    }

    /** Toute demande de retraite de l'agent. */
    public void retraite(Integer employeId, Integer acteurId) {
        signaler(acteurId, documentRhRepository.appliquerDemandesSatisfaites(
                employeId, "RETRAITE", "TRUE"));
    }

    private void signaler(Integer acteurId, List<Map<String, Object>> demandes) {
        for (Map<String, Object> demande : demandes) {
            long id = ((Number) demande.get("id")).longValue();
            notificationRepository.notifierDocumentApplique(id, acteurId);
            Object employe = demande.get("employe_id");
            activiteService.enregistrer(
                    acteurId,
                    employe instanceof Number nombre ? nombre.intValue() : null,
                    "APPLICATION_DOCUMENT",
                    "Demande " + demande.get("reference_document") + " appliquée par l'action directe"
            );
        }
    }
}

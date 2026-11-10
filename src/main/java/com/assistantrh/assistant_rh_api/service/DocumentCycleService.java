package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import com.assistantrh.assistant_rh_api.repository.NotificationRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Cycle de vie d'un document RH : BROUILLON → VALIDE (son auteur valide, la
 * demande prend effet) ou ANNULE, puis ARCHIVE. Les effets propres à chaque
 * type de demande (avancement, mutation, congé) s'ajouteront à la validation.
 */
@Service
public class DocumentCycleService {
    private final DocumentRhRepository documentRhRepository;
    private final NotificationRepository notificationRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ActiviteService activiteService;

    public DocumentCycleService(
            DocumentRhRepository documentRhRepository,
            NotificationRepository notificationRepository,
            UtilisateurRepository utilisateurRepository,
            ActiviteService activiteService
    ) {
        this.documentRhRepository = documentRhRepository;
        this.notificationRepository = notificationRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.activiteService = activiteService;
    }

    /**
     * L'auteur valide sa demande : elle prend effet (les effets propres à
     * chaque type s'ajouteront ici). Le chef du Service du Personnel en est
     * notifié, et l'activité est journalisée. Un document dont l'auteur n'est
     * pas connu (créé avant le suivi des auteurs) se valide par un chef.
     */
    @Transactional
    public void validerDocument(Long id, Integer acteurId) {
        Map<String, Object> document = documentPourCycle(id);
        verifierAuteur(document, acteurId, true);
        changerStatut(document, "VALIDE", List.of("BROUILLON"),
                "Seul un brouillon peut être validé.");
        notificationRepository.notifierDocumentValide(id, acteurId);
        journaliser(document, acteurId, "VALIDATION", "Validation du document ");
    }

    /** L'auteur (ou un chef) renonce à un brouillon : il ne prendra jamais effet. */
    @Transactional
    public void annulerDocument(Long id, Integer acteurId) {
        Map<String, Object> document = documentPourCycle(id);
        verifierAuteur(document, acteurId, false);
        changerStatut(document, "ANNULE", List.of("BROUILLON"),
                "Seul un brouillon peut être annulé.");
        journaliser(document, acteurId, "ANNULATION_DOCUMENT", "Annulation du document ");
    }

    /** Un document validé ou annulé se range aux archives. */
    @Transactional
    public void archiverDocument(Long id, Integer acteurId) {
        Map<String, Object> document = documentPourCycle(id);
        changerStatut(document, "ARCHIVE", List.of("VALIDE", "ANNULE"),
                "Seul un document validé ou annulé peut être archivé.");
        journaliser(document, acteurId, "ARCHIVAGE_DOCUMENT", "Archivage du document ");
    }

    private Map<String, Object> documentPourCycle(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("L'identifiant du document est invalide.");
        }
        return documentRhRepository.trouverPourCycle(id)
                .orElseThrow(() -> new IllegalArgumentException("Le document demandé n'existe pas."));
    }

    /**
     * Seul l'auteur valide sa demande. Sans auteur connu, un chef s'en charge.
     * Un chef peut aussi annuler un brouillon qui n'est pas le sien.
     */
    private void verifierAuteur(Map<String, Object> document, Integer acteurId, boolean validation) {
        if (acteurId == null || acteurId <= 0) {
            throw new IllegalArgumentException("L'utilisateur connecté est inconnu.");
        }
        Object auteur = document.get("auteur_id");
        boolean estAuteur = auteur instanceof Number number && number.intValue() == acteurId;
        boolean chef = utilisateurRepository.estChefActif(acteurId);
        boolean permis = validation ? estAuteur || (auteur == null && chef) : estAuteur || chef;
        if (!permis) {
            throw new IllegalArgumentException(validation
                    ? "Seul l'auteur de la demande peut la valider."
                    : "Seul l'auteur de la demande ou un chef peut l'annuler.");
        }
    }

    private void changerStatut(Map<String, Object> document, String vers, List<String> depuis, String refus) {
        Long id = ((Number) document.get("id")).longValue();
        if (!documentRhRepository.changerStatut(id, vers, depuis)) {
            throw new IllegalArgumentException(refus);
        }
    }

    private void journaliser(Map<String, Object> document, Integer acteurId, String type, String libelle) {
        Object employe = document.get("employe_id");
        Object reference = document.get("reference_document");
        activiteService.enregistrer(
                acteurId,
                employe instanceof Number number ? number.intValue() : null,
                type,
                libelle + (reference == null ? document.get("id") : reference)
        );
    }
}

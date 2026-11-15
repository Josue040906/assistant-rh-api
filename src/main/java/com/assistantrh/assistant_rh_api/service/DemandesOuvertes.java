package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.DocumentRhRepository;
import org.springframework.stereotype.Service;

/**
 * Une opération (avancement, mutation, titularisation, retraite) qui a déjà une
 * demande ouverte pour l'agent se fait par cette demande : on saisit sa réponse
 * signée puis on l'applique. Une décision directe en parallèle est refusée, pour
 * qu'une décision ne soit jamais enregistrée deux fois.
 */
@Service
public class DemandesOuvertes {

    private final DocumentRhRepository documentRhRepository;

    public DemandesOuvertes(DocumentRhRepository documentRhRepository) {
        this.documentRhRepository = documentRhRepository;
    }

    public void verifierAucune(Integer employeId, String typeCode) {
        documentRhRepository.demandeOuverte(employeId, typeCode).ifPresent(demande -> {
            boolean acceptee = "ACCEPTE".equals(demande.get("statut"));
            throw new IllegalArgumentException("La demande " + demande.get("reference_document")
                    + (acceptee ? " est acceptée pour cette opération : appliquez-la."
                                : " attend sa réponse pour cette opération : saisissez la réponse signée, puis appliquez-la.")
                    + " Si elle ne vaut plus, annulez-la d'abord.");
        });
    }
}

package com.assistantrh.assistant_rh_api.model;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record Document(
        Integer id,
        String referenceDocument,
        Integer typeDocumentId,
        Integer employeId,
        String destinataire,
        String objet,
        LocalDate dateDocument,
        LocalDate dateEffet,
        String statut,
        String fichierPath,
        String observation,
        OffsetDateTime dateCreation,
        Map<String, Object> donnees,
        // Le compte qui a créé la demande.
        Integer auteurId,
        // La réponse reçue sur papier (acceptée ou refusée), si elle est saisie.
        DocumentDecision decision
) {
    public Document(
            Integer id, String referenceDocument, Integer typeDocumentId, Integer employeId,
            String destinataire, String objet, LocalDate dateDocument, LocalDate dateEffet,
            String statut, String fichierPath, String observation, OffsetDateTime dateCreation,
            Map<String, Object> donnees, Integer auteurId
    ) {
        this(id, referenceDocument, typeDocumentId, employeId, destinataire, objet, dateDocument,
                dateEffet, statut, fichierPath, observation, dateCreation, donnees, auteurId, null);
    }

    public Document(
            Integer id, String referenceDocument, Integer typeDocumentId, Integer employeId,
            String destinataire, String objet, LocalDate dateDocument, LocalDate dateEffet,
            String statut, String fichierPath, String observation, OffsetDateTime dateCreation,
            Map<String, Object> donnees
    ) {
        this(id, referenceDocument, typeDocumentId, employeId, destinataire, objet, dateDocument,
                dateEffet, statut, fichierPath, observation, dateCreation, donnees, null, null);
    }

    public Document {
        donnees = Collections.unmodifiableMap(
                new LinkedHashMap<>(
                        Objects.requireNonNull(
                                donnees,
                                "Les données du document sont obligatoires."
                        )
                )
        );
    }
}

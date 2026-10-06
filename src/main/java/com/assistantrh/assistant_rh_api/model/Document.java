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
        Map<String, Object> donnees
) {
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

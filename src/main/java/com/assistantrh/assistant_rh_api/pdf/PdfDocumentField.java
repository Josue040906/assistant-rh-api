package com.assistantrh.assistant_rh_api.pdf;

import java.util.Objects;

public record PdfDocumentField(String label, String value) {
    public PdfDocumentField {
        Objects.requireNonNull(label, "Le libellé du champ PDF est obligatoire.");
        Objects.requireNonNull(value, "La valeur du champ PDF est obligatoire.");

        if (label.isBlank() || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Le libellé et la valeur d'un champ PDF ne peuvent pas être vides."
            );
        }
    }
}

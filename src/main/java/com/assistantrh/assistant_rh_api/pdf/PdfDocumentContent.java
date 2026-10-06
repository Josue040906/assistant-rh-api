package com.assistantrh.assistant_rh_api.pdf;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record PdfDocumentContent(
        String administration,
        String devise,
        String titre,
        String reference,
        String nomAgent,
        String matricule,
        String service,
        String poste,
        String destinataire,
        String objet,
        String texte,
        List<PdfDocumentField> champs,
        LocalDate date
) {
    public PdfDocumentContent {
        exigerTexte(administration, "L'administration est obligatoire.");
        exigerTexte(titre, "Le titre du document est obligatoire.");
        exigerTexte(nomAgent, "Le nom de l'agent est obligatoire.");
        exigerTexte(destinataire, "Le destinataire du document est obligatoire.");
        exigerTexte(objet, "L'objet du document est obligatoire.");
        exigerTexte(texte, "Le texte du document est obligatoire.");
        Objects.requireNonNull(champs, "Les champs du document sont obligatoires.");
        Objects.requireNonNull(date, "La date du document est obligatoire.");
        champs = List.copyOf(champs);
    }

    private static void exigerTexte(String valeur, String message) {
        Objects.requireNonNull(valeur, message);
        if (valeur.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}

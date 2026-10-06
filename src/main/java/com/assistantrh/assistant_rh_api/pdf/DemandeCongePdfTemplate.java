package com.assistantrh.assistant_rh_api.pdf;

import com.assistantrh.assistant_rh_api.model.AgentDocumentInfo;
import com.assistantrh.assistant_rh_api.model.Document;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

@Component
public class DemandeCongePdfTemplate {

    private static final DateTimeFormatter FORMAT_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public PdfDocumentContent creer(
            Document document,
            AgentDocumentInfo agent
    ) {
        Objects.requireNonNull(document, "Le document est obligatoire.");
        Objects.requireNonNull(agent, "Les informations de l'agent sont obligatoires.");

        LocalDate dateDebut = lireDate(document, "dateDebut");
        LocalDate dateFin = lireDate(document, "dateFin");
        if (dateFin.isBefore(dateDebut)) {
            throw new IllegalArgumentException(
                    "La date de fin du congé ne peut pas précéder sa date de début."
            );
        }

        long nombreJours = ChronoUnit.DAYS.between(dateDebut, dateFin) + 1;
        String texte = "Je sollicite l'autorisation de prendre un congé "
                + "pour la période indiquée ci-dessous.";

        return new PdfDocumentContent(
                "MINISTERE DE L'ECONOMIE ET FINANCES",
                "Fitiavana tanindrazana fandrosoana",
                "DEMANDE DE CONG\u00c9",
                document.referenceDocument(),
                agent.nomComplet(),
                agent.matricule(),
                agent.service(),
                agent.poste(),
                document.destinataire(),
                "Demande de cong\u00e9",
                texte,
                List.of(
                        new PdfDocumentField(
                                "Date de d\u00e9but",
                                FORMAT_DATE.format(dateDebut)
                        ),
                        new PdfDocumentField(
                                "Date de fin",
                                FORMAT_DATE.format(dateFin)
                        ),
                        new PdfDocumentField(
                                "Nombre de jours",
                                Long.toString(nombreJours)
                        )
                ),
                Objects.requireNonNull(
                        document.dateDocument(),
                        "La date du document est obligatoire pour le PDF."
                )
        );
    }

    private LocalDate lireDate(Document document, String champ) {
        Object valeur = document.donnees().get(champ);
        if (!(valeur instanceof String dateTexte) || dateTexte.isBlank()) {
            throw new IllegalArgumentException(
                    "La donnée '" + champ + "' est absente ou invalide."
            );
        }

        try {
            return LocalDate.parse(dateTexte);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "La donnée '" + champ + "' doit respecter le format AAAA-MM-JJ.",
                    exception
            );
        }
    }
}

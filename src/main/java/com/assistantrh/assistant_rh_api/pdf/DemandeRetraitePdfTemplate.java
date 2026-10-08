package com.assistantrh.assistant_rh_api.pdf;

import com.assistantrh.assistant_rh_api.model.AgentDocumentInfo;
import com.assistantrh.assistant_rh_api.model.Document;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

@Component
public class DemandeRetraitePdfTemplate {

    private static final DateTimeFormatter FORMAT_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public PdfDocumentContent creer(
            Document document,
            AgentDocumentInfo agent
    ) {
        Objects.requireNonNull(document, "Le document est obligatoire.");
        Objects.requireNonNull(agent, "Les informations de l'agent sont obligatoires.");

        LocalDate dateDepart = lireDateDepart(document);
        String texte = "J'ai l'honneur de solliciter mon admission à la retraite "
                + "à compter du " + FORMAT_DATE.format(dateDepart) + ".";

        Object motif = document.donnees().get("motif");
        if (motif instanceof String texteMotif && !texteMotif.isBlank()) {
            texte += "\n\nMotif : " + texteMotif;
        }
        Object observation = document.donnees().get("observation");
        if (observation instanceof String texteObservation
                && !texteObservation.isBlank()) {
            texte += "\n\nObservation : " + texteObservation;
        }

        return new PdfDocumentContent(
                "MINISTERE DE L'ECONOMIE ET FINANCES",
                "Fitiavana tanindrazana fandrosoana",
                "DEMANDE DE RETRAITE",
                document.referenceDocument(),
                agent.nomComplet(),
                agent.matricule(),
                valeurOuNonRenseignee(agent.service()),
                valeurOuNonRenseignee(agent.poste()),
                document.destinataire(),
                "Demande de retraite",
                texte,
                List.of(
                        new PdfDocumentField("Nom", agent.nom()),
                        new PdfDocumentField("Prénom", agent.prenom()),
                        new PdfDocumentField(
                                "Service",
                                valeurOuNonRenseignee(agent.service())
                        ),
                        new PdfDocumentField(
                                "Direction",
                                valeurOuNonRenseignee(agent.direction())
                        ),
                        new PdfDocumentField(
                                "Poste",
                                valeurOuNonRenseignee(agent.poste())
                        ),
                        new PdfDocumentField(
                                "Catégorie",
                                valeurOuNonRenseignee(agent.categorie())
                        ),
                        new PdfDocumentField(
                                "Corps",
                                valeurOuNonRenseignee(agent.corps())
                        ),
                        new PdfDocumentField(
                                "Classe",
                                valeurOuNonRenseignee(agent.classe())
                        ),
                        new PdfDocumentField(
                                "Échelon",
                                agent.echelon() == null
                                        ? "Non renseigné"
                                        : agent.echelon().toString()
                        ),
                        new PdfDocumentField(
                                "Date d'embauche",
                                agent.dateEmbauche() == null
                                        ? "Non renseignée"
                                        : FORMAT_DATE.format(agent.dateEmbauche())
                        ),
                        new PdfDocumentField(
                                "Date souhaitée de départ",
                                FORMAT_DATE.format(dateDepart)
                        ),
                        new PdfDocumentField("Lieu", "________________________")
                ),
                Objects.requireNonNull(
                        document.dateDocument(),
                        "La date du document est obligatoire pour le PDF."
                )
        );
    }

    private LocalDate lireDateDepart(Document document) {
        Object valeur = document.donnees().get("dateDepartSouhaitee");
        if (!(valeur instanceof String dateTexte) || dateTexte.isBlank()) {
            throw new IllegalArgumentException(
                    "La date souhaitée de départ est obligatoire."
            );
        }

        try {
            return LocalDate.parse(dateTexte);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "La date souhaitée de départ doit respecter le format AAAA-MM-JJ.",
                    exception
            );
        }
    }

    private String valeurOuNonRenseignee(String valeur) {
        return valeur == null || valeur.isBlank()
                ? "Non renseigné"
                : valeur;
    }
}

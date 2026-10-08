package com.assistantrh.assistant_rh_api.pdf;

import com.assistantrh.assistant_rh_api.model.AgentDocumentInfo;
import com.assistantrh.assistant_rh_api.model.Document;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class DemandeMutationPdfTemplate {

    private static final DateTimeFormatter FORMAT_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public PdfDocumentContent creer(
            Document document,
            AgentDocumentInfo agent
    ) {
        Objects.requireNonNull(document, "Le document est obligatoire.");
        Objects.requireNonNull(agent, "Les informations de l'agent sont obligatoires.");

        Map<String, Object> situationActuelle =
                lireSituation(document.donnees().get("situationActuelle"));
        Map<String, Object> situationDemandee =
                lireSituation(document.donnees().get("situationDemandee"));
        String dateEffet = formaterDate(
                document.donnees().get("dateEffetSouhaitee")
        );
        String motif = valeur(document.donnees().get("motif"));
        String texte = "J'ai l'honneur de solliciter ma mutation vers "
                + valeur(situationDemandee.get("service"))
                + ", " + valeur(situationDemandee.get("direction"))
                + ", au poste de " + valeur(situationDemandee.get("poste"))
                + ", à " + valeur(situationDemandee.get("lieuTravail")) + ".";
        if (!"Non renseigné".equals(dateEffet)) {
            texte += "\n\nDate d'effet souhaitée : " + dateEffet + ".";
        }
        texte += "\n\nMotif : " + motif;

        return new PdfDocumentContent(
                "MINISTERE DE L'ECONOMIE ET FINANCES",
                "Fitiavana tanindrazana fandrosoana",
                "DEMANDE DE MUTATION",
                document.referenceDocument(),
                agent.nomComplet(),
                agent.matricule(),
                valeur(situationActuelle.get("service")),
                valeur(situationActuelle.get("poste")),
                document.destinataire(),
                "Demande de mutation",
                texte,
                List.of(
                        new PdfDocumentField("Nom", agent.nom()),
                        new PdfDocumentField("Prénom", agent.prenom()),
                        new PdfDocumentField(
                                "Direction actuelle",
                                valeur(situationActuelle.get("direction"))
                        ),
                        new PdfDocumentField(
                                "Service actuel",
                                valeur(situationActuelle.get("service"))
                        ),
                        new PdfDocumentField(
                                "Poste actuel",
                                valeur(situationActuelle.get("poste"))
                        ),
                        new PdfDocumentField(
                                "Lieu de travail actuel",
                                valeur(situationActuelle.get("lieuTravail"))
                        ),
                        new PdfDocumentField(
                                "Direction souhaitée",
                                valeur(situationDemandee.get("direction"))
                        ),
                        new PdfDocumentField(
                                "Service souhaité",
                                valeur(situationDemandee.get("service"))
                        ),
                        new PdfDocumentField(
                                "Poste souhaité",
                                valeur(situationDemandee.get("poste"))
                        ),
                        new PdfDocumentField(
                                "Lieu de travail souhaité",
                                valeur(situationDemandee.get("lieuTravail"))
                        ),
                        new PdfDocumentField("Date d'effet souhaitée", dateEffet),
                        new PdfDocumentField("Motif", motif)
                ),
                Objects.requireNonNull(
                        document.dateDocument(),
                        "La date du document est obligatoire pour le PDF."
                )
        );
    }

    private Map<String, Object> lireSituation(Object valeur) {
        if (!(valeur instanceof Map<?, ?> situation)) {
            throw new IllegalArgumentException(
                    "La situation souhaitée de mutation est introuvable."
            );
        }
        Map<String, Object> resultat = new LinkedHashMap<>();
        situation.forEach((cle, element) -> {
            if (cle instanceof String nom) {
                resultat.put(nom, element);
            }
        });
        return resultat;
    }

    private String formaterDate(Object valeur) {
        if (valeur == null || valeur.toString().isBlank()) {
            return "Non renseigné";
        }
        return LocalDate.parse(valeur.toString()).format(FORMAT_DATE);
    }

    private String valeur(Object valeur) {
        if (valeur == null || valeur.toString().isBlank()) {
            return "Non précisé";
        }
        return valeur.toString();
    }

}

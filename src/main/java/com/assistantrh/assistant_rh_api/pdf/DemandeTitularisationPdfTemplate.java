package com.assistantrh.assistant_rh_api.pdf;

import com.assistantrh.assistant_rh_api.model.AgentDocumentInfo;
import com.assistantrh.assistant_rh_api.model.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class DemandeTitularisationPdfTemplate {

    public PdfDocumentContent creer(Document document, AgentDocumentInfo agent) {
        Objects.requireNonNull(document, "Le document est obligatoire.");
        Objects.requireNonNull(agent, "Les informations de l'agent sont obligatoires.");

        List<PdfDocumentField> champs = new ArrayList<>();
        if (agent.categorie() != null && !agent.categorie().isBlank()) {
            champs.add(new PdfDocumentField("Catégorie", agent.categorie()));
        }
        champs.add(new PdfDocumentField("Situation actuelle",
                situation(document.donnees().get("situationActuelle"), "situationActuelle")));
        champs.add(new PdfDocumentField("Situation demandée",
                situation(document.donnees().get("situationDemandee"), "situationDemandee")));
        Object effet = document.donnees().get("dateEffetSouhaitee");
        if (effet != null) {
            champs.add(new PdfDocumentField("Date d'effet souhaitée", jour(effet.toString())));
        }

        String texte = "Je sollicite ma titularisation dans le corps des fonctionnaires, en conservant "
                + "ma catégorie, au grade dont l'indice est égal ou immédiatement supérieur à mon indice actuel.";
        Object motif = document.donnees().get("motif");
        if (motif instanceof String justification && !justification.isBlank()) {
            texte += "\n\nMotif : " + justification;
        }

        return new PdfDocumentContent(
                "MINISTERE DE L'ECONOMIE ET FINANCES",
                "Fitiavana tanindrazana fandrosoana",
                "DEMANDE DE TITULARISATION",
                document.referenceDocument(),
                agent.nomComplet(),
                agent.matricule(),
                agent.service(),
                agent.poste(),
                document.destinataire(),
                "Demande de titularisation",
                texte,
                champs,
                Objects.requireNonNull(document.dateDocument(), "La date du document est obligatoire pour le PDF.")
        );
    }

    private String situation(Object valeur, String champ) {
        if (!(valeur instanceof Map<?, ?> situation)) {
            throw new IllegalArgumentException("La donnée '" + champ + "' est absente ou invalide.");
        }
        return situation.get("typeEmploi") + " - " + situation.get("gradeLibelle")
                + " (" + situation.get("gradeCode") + ", indice " + situation.get("indice") + ")";
    }

    private static String jour(String date) {
        String[] parties = date.split("-");
        return parties.length == 3 ? parties[2] + "/" + parties[1] + "/" + parties[0] : date;
    }
}

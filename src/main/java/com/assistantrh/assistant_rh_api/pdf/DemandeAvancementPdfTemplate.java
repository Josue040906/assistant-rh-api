package com.assistantrh.assistant_rh_api.pdf;

import com.assistantrh.assistant_rh_api.model.AgentDocumentInfo;
import com.assistantrh.assistant_rh_api.model.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class DemandeAvancementPdfTemplate {

    public PdfDocumentContent creer(
            Document document,
            AgentDocumentInfo agent
    ) {
        Objects.requireNonNull(document, "Le document est obligatoire.");
        Objects.requireNonNull(agent, "Les informations de l'agent sont obligatoires.");

        Map<?, ?> actuelle = lireSituation(
                document.donnees().get("situationActuelle"),
                "situationActuelle"
        );
        Map<?, ?> demandee = lireSituation(
                document.donnees().get("situationDemandee"),
                "situationDemandee"
        );

        List<PdfDocumentField> champs = new ArrayList<>();
        ajouterChampOptionnel(champs, "Catégorie", agent.categorie());
        ajouterChampOptionnel(champs, "Corps", agent.corps());
        champs.add(new PdfDocumentField(
                "Situation actuelle",
                formatterSituation(actuelle)
        ));
        champs.add(new PdfDocumentField(
                "Situation demandée",
                formatterSituation(demandee)
        ));

        Object motif = document.donnees().get("motif");
        String texte = "Je sollicite l'avancement à la situation indiquée "
                + "sur la base de ma carrière administrative.";
        if (motif instanceof String justification && !justification.isBlank()) {
            texte += "\n\nMotif : " + justification;
        }

        return new PdfDocumentContent(
                "MINISTERE DE L'ECONOMIE ET FINANCES",
                "Fitiavana tanindrazana fandrosoana",
                "DEMANDE D'AVANCEMENT",
                document.referenceDocument(),
                agent.nomComplet(),
                agent.matricule(),
                agent.service(),
                agent.poste(),
                document.destinataire(),
                "Demande d'avancement",
                texte,
                champs,
                Objects.requireNonNull(
                        document.dateDocument(),
                        "La date du document est obligatoire pour le PDF."
                )
        );
    }

    private Map<?, ?> lireSituation(Object valeur, String champ) {
        if (!(valeur instanceof Map<?, ?> situation)) {
            throw new IllegalArgumentException(
                    "La donnée '" + champ + "' est absente ou invalide."
            );
        }

        return situation;
    }

    private String formatterSituation(Map<?, ?> situation) {
        Object classe = situation.get("classeLibelle");
        Object echelon = situation.get("echelonOrdre");
        if (!(classe instanceof String classeLibelle)
                || classeLibelle.isBlank()
                || !(echelon instanceof Number echelonOrdre)) {
            throw new IllegalArgumentException(
                    "Les informations de classe ou d'échelon sont invalides."
            );
        }

        return classeLibelle + " - échelon " + echelonOrdre.intValue();
    }

    private void ajouterChampOptionnel(
            List<PdfDocumentField> champs,
            String libelle,
            String valeur
    ) {
        if (valeur != null && !valeur.isBlank()) {
            champs.add(new PdfDocumentField(libelle, valeur));
        }
    }
}

package com.assistantrh.assistant_rh_api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * L'expédition d'un e-mail par l'API HTTP de Brevo (les ports SMTP sont souvent
 * fermés chez les hébergeurs). Sans clé ni adresse d'expéditeur configurées,
 * mode « journal » : rien ne part, l'envoi est seulement noté — on peut tout
 * essayer sans écrire aux agents.
 *
 * Configuration : BREVO_API_KEY, MAIL_EXPEDITEUR_ADRESSE (vérifiée chez Brevo),
 * MAIL_EXPEDITEUR_NOM.
 */
@Component
public class EnvoiEmail {

    private static final Logger LOG = LoggerFactory.getLogger(EnvoiEmail.class);
    private static final URI BREVO = URI.create("https://api.brevo.com/v3/smtp/email");

    private final String cle;
    private final String adresseExpediteur;
    private final String nomExpediteur;
    private final ObjectMapper objectMapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public EnvoiEmail(
            @Value("${brevo.api-key:}") String cle,
            @Value("${mail.expediteur.adresse:}") String adresseExpediteur,
            @Value("${mail.expediteur.nom:SYGPERS – Service du Personnel}") String nomExpediteur,
            ObjectMapper objectMapper
    ) {
        this.cle = cle == null ? "" : cle.strip();
        this.adresseExpediteur = adresseExpediteur == null ? "" : adresseExpediteur.strip();
        this.nomExpediteur = nomExpediteur;
        this.objectMapper = objectMapper;
    }

    /** Vrai si les e-mails partent réellement (clé et expéditeur configurés). */
    public boolean reel() {
        return !cle.isEmpty() && !adresseExpediteur.isEmpty();
    }

    public String expediteur() {
        return reel() ? nomExpediteur + " <" + adresseExpediteur + ">" : nomExpediteur;
    }

    /** Envoie l'e-mail ; la référence du fournisseur, ou « JOURNAL » en mode journal. Lève une exception si refusé. */
    public String envoyer(String adresse, String nom, String sujet, String texte) {
        if (!reel()) {
            LOG.info("[mode journal] E-mail non envoyé à {} : « {} »", adresse, sujet);
            return "JOURNAL";
        }
        Map<String, Object> corps = Map.of(
                "sender", Map.of("name", nomExpediteur, "email", adresseExpediteur),
                "to", List.of(nom == null || nom.isBlank() ? Map.of("email", adresse) : Map.of("email", adresse, "name", nom)),
                "subject", sujet,
                "textContent", texte
        );
        try {
            HttpRequest requete = HttpRequest.newBuilder(BREVO)
                    .timeout(Duration.ofSeconds(20))
                    .header("api-key", cle)
                    .header("accept", "application/json")
                    .header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(corps)))
                    .build();
            HttpResponse<String> reponse = client.send(requete, HttpResponse.BodyHandlers.ofString());
            if (reponse.statusCode() / 100 != 2) {
                throw new IllegalStateException("Brevo a refusé l'envoi (" + reponse.statusCode() + ") : "
                        + raison(reponse.body()));
            }
            Object id = objectMapper.readValue(reponse.body(), Map.class).get("messageId");
            return id == null ? "BREVO" : id.toString();
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Envoi interrompu.", interruption);
        } catch (java.io.IOException reseau) {
            throw new IllegalStateException("Brevo est injoignable : " + reseau.getMessage(), reseau);
        }
    }

    private String raison(String corps) {
        try {
            Object message = objectMapper.readValue(corps, Map.class).get("message");
            return message == null ? corps : message.toString();
        } catch (RuntimeException illisible) {
            return corps;
        }
    }
}

package com.assistantrh.assistant_rh_api.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GeminiService {

    private final Client client;

    // Récupère la clé depuis application.properties ou l'environnement GEMINI_API_KEY
    public GeminiService(@Value("${gemini.api.key:${GEMINI_API_KEY:}}") String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            this.client = Client.builder().apiKey(apiKey).build();
        } else {
            // Si aucune clé n'est injectée explicitement, le SDK cherche la variable GEMINI_API_KEY
            this.client = new Client();
        }
    }

    public String envoyerMessage(String messageUtilisateur) {
        // Utilisation du modèle gemini-2.5-flash
        GenerateContentResponse response = client.models.generateContent(
                "gemini-2.5-flash",
                messageUtilisateur,
                null
        );

        return response.text();
    }
}
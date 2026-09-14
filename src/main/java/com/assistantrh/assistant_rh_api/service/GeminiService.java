package com.assistantrh.assistant_rh_api.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GeminiService {

    private final Client client;

    public GeminiService(@Value("${gemini.api.key:${GEMINI_API_KEY:}}") String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            this.client = Client.builder().apiKey(apiKey).build();
        } else {
            this.client = new Client();
        }
    }

    public String envoyerMessage(String messageUtilisateur) {
        try {
            // Utilisation du modèle gemini-3.6-flash
            GenerateContentResponse response = client.models.generateContent(
                    "gemini-3.6-flash",
                    messageUtilisateur,
                    null
            );

            return response.text();
        } catch (Exception e) {
            e.printStackTrace();
            return "Erreur lors de la communication avec l'API Gemini : " + e.getMessage();
        }
    }
}
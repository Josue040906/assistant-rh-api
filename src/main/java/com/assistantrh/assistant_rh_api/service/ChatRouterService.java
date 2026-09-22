package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.controller.ChatResponse;
import org.springframework.stereotype.Service;

@Service
public class ChatRouterService {

    private final GeminiService geminiService;

    public ChatRouterService(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    public ChatResponse router(String message) {

        if (message == null || message.isBlank()) {
            return new ChatResponse(
                    "text",
                    "Veuillez saisir une demande."
            );
        }

        return geminiService.envoyerMessage(message.trim());
    }
}
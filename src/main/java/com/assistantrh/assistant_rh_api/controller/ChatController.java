package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.GeminiService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final GeminiService geminiService;

    public ChatController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return geminiService.envoyerMessage(request.message());
    }

    public record ChatRequest(String message) {
    }
}
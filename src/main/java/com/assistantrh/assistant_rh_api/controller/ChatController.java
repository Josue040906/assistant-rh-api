package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.ChatRouterService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatRouterService chatRouterService;

    public ChatController(ChatRouterService chatRouterService) {
        this.chatRouterService = chatRouterService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return chatRouterService.router(request.message());
    }

    public record ChatRequest(String message) {
    }
}
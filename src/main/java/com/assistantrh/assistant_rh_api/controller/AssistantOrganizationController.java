package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.AssistantOrganizationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant/organization")
public class AssistantOrganizationController {
    private final AssistantOrganizationService service;

    public AssistantOrganizationController(AssistantOrganizationService service) {
        this.service = service;
    }

    @GetMapping
    public AssistantOrganizationService.OrganizationResponse search(
            @RequestParam String query,
            @RequestParam(required = false) String scope
    ) {
        return service.search(query, scope);
    }
}

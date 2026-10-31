package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.OrganisationService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/organisation")
public class OrganisationController {
    private final OrganisationService service;

    public OrganisationController(OrganisationService service) {
        this.service = service;
    }

    @PostMapping("/consultation")
    public Map<String, Object> consulter(@RequestBody ConsultationRequest request) {
        return service.consulter(request);
    }

    public record ConsultationRequest(
            String operation,
            Long directionId,
            Long serviceId,
            Long posteId,
            String query,
            Boolean includeVacant
    ) {}
}

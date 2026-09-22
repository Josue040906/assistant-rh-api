package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.ServiceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final ServiceService serviceService;

    public ServiceController(ServiceService serviceService) {
        this.serviceService = serviceService;
    }

    @GetMapping
    public List<Map<String, Object>> getAllServices() {
        return serviceService.getAllServices();
    }

    @GetMapping("/recherche")
    public List<Map<String, Object>> rechercherServices(
            @RequestParam String query
    ) {
        return serviceService.rechercherServices(query);
    }
}
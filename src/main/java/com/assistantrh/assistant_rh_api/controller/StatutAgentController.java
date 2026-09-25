        package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.StatutAgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/statuts")
@CrossOrigin
public class StatutAgentController {

    private final StatutAgentService statutAgentService;

    public StatutAgentController(
            StatutAgentService statutAgentService) {
        this.statutAgentService = statutAgentService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllStatuts() {
        List<Map<String, Object>> statuts =
                statutAgentService.getAllStatuts();

        Map<String, Object> response = new HashMap<>();
        response.put("value", statuts);
        response.put("Count", statuts.size());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getStatutById(
            @PathVariable Long id) {

        Map<String, Object> statut =
                statutAgentService.getStatutById(id);

        if (statut == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(statut);
    }
}

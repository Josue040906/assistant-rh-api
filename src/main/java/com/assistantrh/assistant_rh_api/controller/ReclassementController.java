package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import com.assistantrh.assistant_rh_api.service.ReclassementService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
public class ReclassementController {

    private final ReclassementService reclassementService;

    public ReclassementController(ReclassementService reclassementService) {
        this.reclassementService = reclassementService;
    }

    @GetMapping("/api/carrieres/employes/{id:\\d+}/reclassement")
    public Map<String, Object> proposerReclassement(
            @PathVariable Integer id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer acteurId
    ) {
        return reclassementService.proposer(id, acteurId);
    }

    @PostMapping({
            "/api/employes/{id:\\d+}/reclassement",
            "/api/carrieres/employes/{id:\\d+}/reclassement"
    })
    public Map<String, Object> confirmerReclassement(
            @PathVariable Integer id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer acteurId,
            @RequestBody(required = false) DateEffetRequest request
    ) {
        return reclassementService.confirmer(
                id, acteurId, request == null ? null : request.dateEffet()
        );
    }

    @GetMapping("/api/carrieres/employes/{id:\\d+}/historique-reclassements")
    public List<Map<String, Object>> obtenirHistorique(
            @PathVariable Integer id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
            Integer acteurId
    ) {
        return reclassementService.obtenirHistorique(id, acteurId);
    }

    public record DateEffetRequest(LocalDate dateEffet) {
    }

}

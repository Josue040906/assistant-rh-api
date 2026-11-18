package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import com.assistantrh.assistant_rh_api.service.MessageAgentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Les e-mails aux agents : brouillons proposés par les événements de carrière,
 * envoyés ou écartés par le Service du Personnel. Statuts : PROPOSE, ENVOYE,
 * ECARTE, ECHEC.
 */
@RestController
@RequestMapping("/api/messages-agents")
public class MessageAgentController {

    private final MessageAgentService service;

    public MessageAgentController(MessageAgentService service) {
        this.service = service;
    }

    @GetMapping
    public List<Map<String, Object>> lister(
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) Integer employeId,
            // Seulement les messages préparés depuis ce nombre de minutes (juste après une action).
            @RequestParam(required = false) Integer recentsMinutes,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE) Integer acteurId
    ) {
        return service.lister(statut, employeId, recentsMinutes, acteurId);
    }

    /** Envoi réel ou mode journal, et l'expéditeur affiché. */
    @GetMapping("/mode")
    public Map<String, Object> mode() {
        return service.mode();
    }

    /** Envoie le message tel que relu : {"sujet", "corps"} (facultatifs : le brouillon sinon). */
    @PostMapping("/{id:\\d+}/envoyer")
    public ResponseEntity<?> envoyer(
            @PathVariable long id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE) Integer acteurId,
            @RequestBody(required = false) Map<String, Object> corps
    ) {
        try {
            return ResponseEntity.ok(service.envoyer(id, acteurId,
                    corps == null || corps.get("sujet") == null ? null : corps.get("sujet").toString(),
                    corps == null || corps.get("corps") == null ? null : corps.get("corps").toString()));
        } catch (IllegalStateException echec) {
            // Le fournisseur a refusé : l'échec est enregistré, on peut réessayer.
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", echec.getMessage()));
        }
    }

    @PostMapping("/{id:\\d+}/ecarter")
    public Map<String, Object> ecarter(
            @PathVariable long id,
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE) Integer acteurId
    ) {
        service.ecarter(id, acteurId);
        return Map.of("id", id, "message", "Message écarté.");
    }
}

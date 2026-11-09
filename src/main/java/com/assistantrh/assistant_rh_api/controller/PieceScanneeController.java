package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.security.ApiAuthenticationInterceptor;
import com.assistantrh.assistant_rh_api.service.PieceScanneeService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Pièces scannées attachées à un document ou à un diplôme. */
@RestController
@RequestMapping("/api/pieces-scannees")
public class PieceScanneeController {
    private final PieceScanneeService service;

    public PieceScanneeController(PieceScanneeService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> deposer(
            @RequestAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE) Integer acteurId,
            @RequestParam String cibleType,
            @RequestParam Integer cibleId,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.deposer(
                acteurId, cibleType, cibleId, file.getOriginalFilename(), file.getBytes()));
    }

    @GetMapping
    public List<Map<String, Object>> lister(
            @RequestParam String cibleType,
            @RequestParam Integer cibleId
    ) {
        return service.lister(cibleType, cibleId);
    }

    @GetMapping("/{id:\\d+}/fichier")
    public ResponseEntity<byte[]> fichier(@PathVariable Integer id) throws IOException {
        return service.lire(id)
                .map(piece -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(piece.typeMime()))
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                                .filename(piece.nomFichier(), StandardCharsets.UTF_8).build().toString())
                        .body(piece.octets()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

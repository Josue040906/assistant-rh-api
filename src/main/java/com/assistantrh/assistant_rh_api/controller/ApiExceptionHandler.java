package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.CinDejaUtiliseException;
import com.assistantrh.assistant_rh_api.service.LocaliteInvalideException;
import com.assistantrh.assistant_rh_api.service.TelephoneInvalideException;
import com.assistantrh.assistant_rh_api.service.DiplomeDejaEnregistreException;
import com.assistantrh.assistant_rh_api.service.ProfilAgentAmbiguException;
import com.assistantrh.assistant_rh_api.service.ReclassementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(CinDejaUtiliseException.class)
    public ResponseEntity<Map<String, String>> gererCinDejaUtilise(
            CinDejaUtiliseException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler({
            LocaliteInvalideException.class,
            TelephoneInvalideException.class
    })
    public ResponseEntity<Map<String, String>> gererDonneesProfilInvalides(
            RuntimeException exception
    ) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(DiplomeDejaEnregistreException.class)
    public ResponseEntity<Map<String, String>> gererDiplomeDuplique(
            DiplomeDejaEnregistreException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(ReclassementException.class)
    public ResponseEntity<Map<String, String>> gererErreurReclassement(
            ReclassementException exception
    ) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of(
                        "code", exception.getCode(),
                        "message", exception.getMessage()
                ));
    }

    @ExceptionHandler(ProfilAgentAmbiguException.class)
    public ResponseEntity<Map<String, Object>> gererProfilAgentAmbigu(
            ProfilAgentAmbiguException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "code", "AGENT_AMBIGU",
                        "message", exception.getMessage(),
                        "candidats", exception.getCandidats()
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> gererRequeteInvalide(
            IllegalArgumentException exception
    ) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> gererCorpsJsonInvalide(
            HttpMessageNotReadableException exception
    ) {
        return ResponseEntity.badRequest()
                .body(Map.of(
                        "code", "INVALID_REQUEST",
                        "message", "Le corps JSON est invalide ou incomplet."
                ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> gererOperationImpossible(
            IllegalStateException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> gererDependanceIndisponible(
            DataAccessException exception
    ) {
        log.error("Échec d'accès aux données RH (DataAccessException).", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "code", "RH_DATA_UNAVAILABLE",
                        "message", "Le service de donnees RH est temporairement indisponible."
                ));
    }
}

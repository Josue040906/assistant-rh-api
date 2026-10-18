package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.CinDejaUtiliseException;
import com.assistantrh.assistant_rh_api.service.LocaliteInvalideException;
import com.assistantrh.assistant_rh_api.service.TelephoneInvalideException;
import com.assistantrh.assistant_rh_api.service.DiplomeDejaEnregistreException;
import com.assistantrh.assistant_rh_api.service.ReclassementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> gererRequeteInvalide(
            IllegalArgumentException exception
    ) {
        return ResponseEntity.badRequest()
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> gererOperationImpossible(
            IllegalStateException exception
    ) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", exception.getMessage()));
    }
}

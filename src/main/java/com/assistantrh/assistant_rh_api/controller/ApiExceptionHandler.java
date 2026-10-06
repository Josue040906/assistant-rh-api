package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.CinDejaUtiliseException;
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
}

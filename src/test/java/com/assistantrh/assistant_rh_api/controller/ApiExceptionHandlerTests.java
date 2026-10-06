package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.CinDejaUtiliseException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTests {

    @Test
    void retourneUnConflitAvecUnMessageLisiblePourUnCinDuplique() {
        ResponseEntity<Map<String, String>> response =
                new ApiExceptionHandler().gererCinDejaUtilise(
                        new CinDejaUtiliseException()
                );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(
                CinDejaUtiliseException.MESSAGE,
                response.getBody().get("message")
        );
    }
}

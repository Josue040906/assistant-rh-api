package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.CinDejaUtiliseException;
import com.assistantrh.assistant_rh_api.service.ReclassementException;
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

    @Test
    void conserveLeCodeMetierEtLeStatutDesErreursDeReclassement() {
        ResponseEntity<Map<String, String>> response =
                new ApiExceptionHandler().gererErreurReclassement(
                        new ReclassementException(
                                "EMPLOYE_NOT_FOUND",
                                HttpStatus.NOT_FOUND,
                                "Agent absent."
                        )
                );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("EMPLOYE_NOT_FOUND", response.getBody().get("code"));
        assertEquals("Agent absent.", response.getBody().get("message"));
    }
}

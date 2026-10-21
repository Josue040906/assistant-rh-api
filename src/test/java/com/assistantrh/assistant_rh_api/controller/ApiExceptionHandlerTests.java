package com.assistantrh.assistant_rh_api.controller;

import com.assistantrh.assistant_rh_api.service.CinDejaUtiliseException;
import com.assistantrh.assistant_rh_api.service.ReclassementException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
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

    @Test
    void masqueLesDetailsDesErreursDeDependanceDeDonnees() {
        ResponseEntity<Map<String, String>> response =
                new ApiExceptionHandler().gererDependanceIndisponible(
                        new DataAccessResourceFailureException("SQL interne")
                );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("RH_DATA_UNAVAILABLE", response.getBody().get("code"));
        assertEquals(
                "Le service de donnees RH est temporairement indisponible.",
                response.getBody().get("message")
        );
    }

    @Test
    void retourneUneErreurDeDependanceGeneriqueSurTimeoutSql() {
        ResponseEntity<Map<String, String>> response =
                new ApiExceptionHandler().gererDependanceIndisponible(
                        new QueryTimeoutException("timeout interne")
                );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("RH_DATA_UNAVAILABLE", response.getBody().get("code"));
    }

    @Test
    void retourneUneErreurStablePourUnCorpsJsonIllisible() {
        ResponseEntity<Map<String, String>> response =
                new ApiExceptionHandler().gererCorpsJsonInvalide(
                        new HttpMessageNotReadableException(
                                "JSON interne invalide",
                                new MockHttpInputMessage(new byte[0])
                        )
                );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_REQUEST", response.getBody().get("code"));
        assertEquals(
                "Le corps JSON est invalide ou incomplet.",
                response.getBody().get("message")
        );
    }
}

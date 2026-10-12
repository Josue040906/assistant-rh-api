package com.assistantrh.assistant_rh_api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TelephoneServiceTests {

    private final TelephoneService service = new TelephoneService("MG");

    @Test
    void normaliseLesNumerosNationauxEtInternationauxMalgaches() {
        assertEquals(
                "+261341234567",
                service.normaliser("034 12 345 67")
        );
        assertEquals(
                "+261341234567",
                service.normaliser("+261 34 12 345 67")
        );
    }

    @Test
    void accepteUnNumeroEtrangerInternationalValide() {
        assertEquals(
                "+12025550123",
                service.normaliser("+1 202 555 0123")
        );
    }

    @Test
    void refuseUnNumeroInvalideSansLeTransformer() {
        assertThrows(
                TelephoneInvalideException.class,
                () -> service.normaliser("034 12")
        );
    }

    @Test
    void conserveUnTelephoneOptionnelVideCommeNull() {
        assertNull(service.normaliser("  "));
    }
}

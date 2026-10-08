package com.assistantrh.assistant_rh_api.security;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BearerTokenServiceTests {

    private static final String SECRET =
            "test-secret-for-hmac-signing-with-at-least-32-bytes";

    @Test
    void creeEtValideUnJetonSigne() {
        BearerTokenService service =
                new BearerTokenService(new ObjectMapper(), SECRET);
        String jeton = service.creerJeton(42);

        assertEquals(42, service.validerEtLireUtilisateur(jeton));
    }

    @Test
    void refuseUnJetonModifieOuSigneAvecUneAutreCle() {
        BearerTokenService service =
                new BearerTokenService(new ObjectMapper(), SECRET);
        BearerTokenService autreCle =
                new BearerTokenService(
                        new ObjectMapper(),
                        "another-secret-key-with-at-least-32-bytes"
                );
        String jeton = service.creerJeton(42);
        int debutSignature = jeton.lastIndexOf('.') + 1;
        String signature = jeton.substring(debutSignature);
        String signatureModifiee =
                (signature.startsWith("a") ? "b" : "a")
                        + signature.substring(1);
        String jetonModifie = jeton.substring(0, debutSignature)
                + signatureModifiee;

        assertNull(service.validerEtLireUtilisateur(jetonModifie));
        assertNull(autreCle.validerEtLireUtilisateur(jeton));
        assertNull(service.validerEtLireUtilisateur("not-a-jwt"));
    }

    @Test
    void refuseLaCreationSiLaCleEstTropCourte() {
        BearerTokenService service =
                new BearerTokenService(new ObjectMapper(), "short");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> service.creerJeton(42)
        );
    }
}

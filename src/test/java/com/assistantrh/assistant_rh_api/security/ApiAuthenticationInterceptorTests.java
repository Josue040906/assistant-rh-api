package com.assistantrh.assistant_rh_api.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiAuthenticationInterceptorTests {

    private static final String SECRET =
            "test-secret-for-hmac-signing-with-at-least-32-bytes";

    private final BearerTokenService tokenService =
            new BearerTokenService(new ObjectMapper(), SECRET);
    private final ApiAuthenticationInterceptor interceptor =
            new ApiAuthenticationInterceptor(tokenService);

    @Test
    void exigeUnJetonPourLeFluxPersonnelActivites() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/activites"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void ajouteLIdentiteVerifieeAuContexteDeRequete() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/activites"
        );
        request.addHeader(
                "Authorization",
                "Bearer " + tokenService.creerJeton(42)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(
                42,
                request.getAttribute(
                        ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE
                )
        );
    }

    @Test
    void laisseLesRoutesPubliquesLoginEtInscriptionAccessibles() throws Exception {
        MockHttpServletRequest login = new MockHttpServletRequest(
                "POST",
                "/api/utilisateurs/login"
        );
        MockHttpServletRequest inscription = new MockHttpServletRequest(
                "POST",
                "/api/utilisateurs/inscription"
        );

        assertTrue(interceptor.preHandle(
                login,
                new MockHttpServletResponse(),
                new Object()
        ));
        assertTrue(interceptor.preHandle(
                inscription,
                new MockHttpServletResponse(),
                new Object()
        ));
    }
}

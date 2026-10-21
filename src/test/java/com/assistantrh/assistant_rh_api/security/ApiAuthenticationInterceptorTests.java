package com.assistantrh.assistant_rh_api.security;

import com.assistantrh.assistant_rh_api.service.UtilisateurService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiAuthenticationInterceptorTests {

    private static final String SECRET =
            "test-secret-for-hmac-signing-with-at-least-32-bytes";

    private final BearerTokenService tokenService =
            new BearerTokenService(new ObjectMapper(), SECRET);
    private final UtilisateurService utilisateurService =
            mock(UtilisateurService.class);
    private final ApiAuthenticationInterceptor interceptor =
            new ApiAuthenticationInterceptor(tokenService, utilisateurService);

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
    void protegeLesNotificationsEtExigeUnCompteSpersActif() throws Exception {
        for (String[] route : new String[][]{
                {"GET", "/api/notifications"},
                {"PATCH", "/api/notifications/12/lue"},
                {"PATCH", "/api/notifications/lues"}
        }) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest(route[0], route[1]);
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(request, response, new Object()));
            assertEquals(401, response.getStatus());
        }

        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/notifications"
        );
        request.addHeader(
                "Authorization",
                "Bearer " + tokenService.creerJeton(42)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(utilisateurService.estAgentSpersActif(42)).thenReturn(true);
        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(
                42,
                request.getAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
        );

        when(utilisateurService.estAgentSpersActif(42)).thenReturn(false);
        request = new MockHttpServletRequest("GET", "/api/notifications");
        request.addHeader(
                "Authorization",
                "Bearer " + tokenService.creerJeton(42)
        );
        response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
    }

    @Test
    void exigeUnJetonPourLesDiplomesEtPropositionsDeCarriere() throws Exception {
        for (String path : new String[]{
                "/api/employes/12/diplomes",
                "/api/employes/12/affectation",
                "/api/employes/12/affectations",
                "/api/referentiels/diplomes/options",
                "/api/employes/12/reclassement",
                "/api/carrieres/employes/12/reclassement",
                "/api/carrieres/employes/12/avancement",
                "/api/referentiels/reclassements",
                "/api/referentiels/reclassements/options",
                "/api/profil/agent",
                "/api/profil/agent/mon-profil"
        }) {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("GET", path);
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertFalse(interceptor.preHandle(request, response, new Object()));
            assertEquals(401, response.getStatus());
        }
    }

    @Test
    void protectsOwnProfileAndRequiresAnActiveSpersAgent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/profil/agent/mon-profil"
        );
        request.addHeader(
                "Authorization",
                "Bearer " + tokenService.creerJeton(42)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(utilisateurService.estAgentSpersActif(42)).thenReturn(false);
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());

        request = new MockHttpServletRequest(
                "GET",
                "/api/profil/agent/mon-profil"
        );
        request.addHeader(
                "Authorization",
                "Bearer " + tokenService.creerJeton(42)
        );
        response = new MockHttpServletResponse();
        when(utilisateurService.estAgentSpersActif(42)).thenReturn(true);

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(
                42,
                request.getAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
        );
    }

    @Test
    void exigeUnJetonPourToutesLesRecherchesAgents() throws Exception {
        for (String path : new String[]{
                "/api/recherche/agents",
                "/api/recherche/agents/approximative",
                "/api/recherche/agents/criteres"
        }) {
            MockHttpServletRequest request = new MockHttpServletRequest(
                    "GET",
                    path
            );
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertFalse(interceptor.preHandle(request, response, new Object()));
            assertEquals(401, response.getStatus());
        }

        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/recherche/agents/criteres"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());

        request.addHeader(
                "Authorization",
                "Bearer " + tokenService.creerJeton(42)
        );
        when(utilisateurService.estAgentSpersActif(42)).thenReturn(true);
        response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(
                42,
                request.getAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE)
        );
    }

    @Test
    void protectsEmployeePhotoFilesWithAnActiveAgentToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/uploads/employes/agent-photo.jpg"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());

        request = new MockHttpServletRequest(
                "GET",
                "/uploads/employes/agent-photo.jpg"
        );
        request.addHeader(
                "Authorization",
                "Bearer " + tokenService.creerJeton(42)
        );
        response = new MockHttpServletResponse();
        when(utilisateurService.estAgentSpersActif(42)).thenReturn(false);
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());

        request = new MockHttpServletRequest(
                "GET",
                "/uploads/employes/agent-photo.jpg"
        );
        request.addHeader(
                "Authorization",
                "Bearer " + tokenService.creerJeton(42)
        );
        response = new MockHttpServletResponse();
        when(utilisateurService.estAgentSpersActif(42)).thenReturn(true);
        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void refuseUnCompteInactifOuSansAutorisationPourLaRecherche() throws Exception {
        when(utilisateurService.estAgentSpersActif(42)).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/recherche/agents/approximative"
        );
        request.addHeader(
                "Authorization",
                "Bearer " + tokenService.creerJeton(42)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
    }

    @Test
    void refuseUnJetonInvalideSurLesRoutesDeRecherche() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/recherche/agents"
        );
        request.addHeader("Authorization", "Bearer invalid-token");
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

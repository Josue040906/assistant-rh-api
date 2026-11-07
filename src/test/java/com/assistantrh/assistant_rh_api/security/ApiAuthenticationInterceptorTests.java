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
    void protegeLaConsultationDeLOrganisationEtExigeUnCompteSpersActif() throws Exception {
        MockHttpServletRequest anonymous = new MockHttpServletRequest(
                "GET", "/api/organisation"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(anonymous, response, new Object()));
        assertEquals(401, response.getStatus());

        when(utilisateurService.estAgentSpersActif(42)).thenReturn(false);
        MockHttpServletRequest inactive = new MockHttpServletRequest(
                "GET", "/api/organisation"
        );
        inactive.addHeader("Authorization", "Bearer " + tokenService.creerJeton(42));
        response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(inactive, response, new Object()));
        assertEquals(403, response.getStatus());

        when(utilisateurService.estAgentSpersActif(42)).thenReturn(true);
        MockHttpServletRequest active = new MockHttpServletRequest(
                "GET", "/api/organisation"
        );
        active.addHeader("Authorization", "Bearer " + tokenService.creerJeton(42));
        assertTrue(interceptor.preHandle(active, new MockHttpServletResponse(), new Object()));
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
                "/api/recherche/agents/criteres",
                "/api/recherche/agents/avancee"
        }) {
            MockHttpServletRequest request = new MockHttpServletRequest(
                    "GET",
                    path
            );
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertFalse(interceptor.preHandle(request, response, new Object()));
            assertEquals(401, response.getStatus());
        }

        for (String path : new String[]{
                "/api/recherche/agents/criteres",
                "/api/recherche/agents/avancee"
        }) {
            MockHttpServletRequest request = new MockHttpServletRequest(
                    "POST",
                    path
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
                    request.getAttribute(
                            ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE
                    )
            );

            when(utilisateurService.estAgentSpersActif(42)).thenReturn(false);
            request = new MockHttpServletRequest("POST", path);
            request.addHeader(
                    "Authorization",
                    "Bearer " + tokenService.creerJeton(42)
            );
            response = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(request, response, new Object()));
            assertEquals(403, response.getStatus());
        }
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

    @Test
    void exigeUneAuthentificationSurToutesLesRoutesDeConsultationRh() throws Exception {
        for (String path : new String[]{
                "/api/employes",
                "/api/employes/42",
                "/api/employes/profil",
                "/api/employes/recherche",
                "/api/employes/referentiels",
                "/api/profil",
                "/api/recherche/agents",
                "/api/referentiels/services"
        }) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            MockHttpServletResponse response = new MockHttpServletResponse();

            assertFalse(interceptor.preHandle(request, response, new Object()));
            assertEquals(401, response.getStatus(), path);
        }
    }

    @Test
    void exigeUnCompteSpersActifPourConsulterLesDonneesDesEmployes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/employes"
        );
        request.addHeader(
                "Authorization",
                "Bearer " + tokenService.creerJeton(42)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(utilisateurService.estAgentSpersActif(42)).thenReturn(false);
        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());

        request = new MockHttpServletRequest("GET", "/api/employes/42");
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
    void appliqueLesReglesDeSecuriteAvecUnContextPath() throws Exception {
        MockHttpServletRequest publicRequest = new MockHttpServletRequest(
                "POST",
                "/rh/api/utilisateurs/login"
        );
        publicRequest.setContextPath("/rh");
        assertTrue(interceptor.preHandle(
                publicRequest,
                new MockHttpServletResponse(),
                new Object()
        ));

        MockHttpServletRequest rhRequest = new MockHttpServletRequest(
                "GET",
                "/rh/api/employes"
        );
        rhRequest.setContextPath("/rh");
        rhRequest.addHeader(
                "Authorization",
                "Bearer " + tokenService.creerJeton(42)
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(utilisateurService.estAgentSpersActif(42)).thenReturn(false);

        assertFalse(interceptor.preHandle(rhRequest, response, new Object()));
        assertEquals(403, response.getStatus());
    }

}

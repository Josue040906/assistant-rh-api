package com.assistantrh.assistant_rh_api.security;

import com.assistantrh.assistant_rh_api.service.UtilisateurService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.assistantrh.assistant_rh_api.controller.UtilisateurController;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoleAuthorizationInterceptorTests {

    private final UtilisateurService utilisateurService =
            mock(UtilisateurService.class);
    private final RoleAuthorizationInterceptor interceptor =
            new RoleAuthorizationInterceptor(utilisateurService);
    private final ChefOnlyController controller = new ChefOnlyController();

    @Test
    void refusesChefOnlyOperationWithoutVerifiedIdentity() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/admin");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(
                request, response, handler("chefOnly")
        ));
        assertEquals(401, response.getStatus());
    }

    @Test
    void refusesAgentAndAllowsActiveChef() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/admin");
        request.setAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE, 17);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(utilisateurService.estChefActif(17)).thenReturn(false);

        assertFalse(interceptor.preHandle(
                request, response, handler("chefOnly")
        ));
        assertEquals(403, response.getStatus());

        request = new MockHttpServletRequest("PUT", "/api/admin");
        request.setAttribute(ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE, 23);
        response = new MockHttpServletResponse();
        when(utilisateurService.estChefActif(23)).thenReturn(true);

        assertTrue(interceptor.preHandle(
                request, response, handler("chefOnly")
        ));
    }

    @Test
    void doesNotRestrictUnannotatedHandlers() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/read");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(
                request, response, handler("ordinary")
        ));
    }


    @Test
    void accountEmailLookupIsDeclaredChefOnly() throws Exception {
        assertNotNull(UtilisateurController.class
                .getMethod("trouverParEmail", String.class)
                .getAnnotation(RequiresSpersChef.class));
    }

    private HandlerMethod handler(String methodName) throws Exception {
        return new HandlerMethod(
                controller,
                ChefOnlyController.class.getMethod(methodName)
        );
    }

    static class ChefOnlyController {
        @RequiresSpersChef
        public void chefOnly() {}

        public void ordinary() {}
    }
}

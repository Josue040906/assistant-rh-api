package com.assistantrh.assistant_rh_api.security;

import com.assistantrh.assistant_rh_api.service.UtilisateurService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ApiAuthenticationInterceptor implements HandlerInterceptor {

    public static final String USER_ID_ATTRIBUTE =
            "authenticatedUserId";
    private static final String LOGIN_PATH = "/api/utilisateurs/login";
    private static final String SIGNUP_PATH = "/api/utilisateurs/inscription";

    private final BearerTokenService bearerTokenService;
    private final UtilisateurService utilisateurService;

    public ApiAuthenticationInterceptor(
            BearerTokenService bearerTokenService,
            UtilisateurService utilisateurService
    ) {
        this.bearerTokenService = bearerTokenService;
        this.utilisateurService = utilisateurService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || estRoutePublique(request)) {
            return true;
        }

        /*
         * Fail closed: every API route other than login/registration requires
         * an authenticated, currently active SPERS account. This prevents a
         * newly added GET endpoint from becoming public merely because it was
         * not added to a list of protected paths.
         */
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return refuser(response);
        }

        Integer utilisateurId = bearerTokenService.validerEtLireUtilisateur(
                authorization.substring("Bearer ".length()).trim()
        );
        if (utilisateurId == null) {
            return refuser(response);
        }

        request.setAttribute(USER_ID_ATTRIBUTE, utilisateurId);
        if (!utilisateurService.estAgentSpersActif(utilisateurId)) {
            return interdire(response);
        }
        return true;
    }

    private boolean estRoutePublique(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "POST".equalsIgnoreCase(request.getMethod())
                && (LOGIN_PATH.equals(path) || SIGNUP_PATH.equals(path));
    }

    private boolean refuser(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentification requise ou jeton invalide.\"}"
        );
        return false;
    }

    private boolean interdire(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"code\":\"FORBIDDEN\",\"message\":\"Un compte SPERS actif est requis pour accéder à cette ressource.\"}"
        );
        return false;
    }
}

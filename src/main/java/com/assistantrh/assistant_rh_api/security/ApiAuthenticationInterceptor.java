package com.assistantrh.assistant_rh_api.security;

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

    public ApiAuthenticationInterceptor(
            BearerTokenService bearerTokenService
    ) {
        this.bearerTokenService = bearerTokenService;
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

        boolean operationEcrit =
                !"GET".equalsIgnoreCase(request.getMethod())
                        && !"HEAD".equalsIgnoreCase(request.getMethod());
        boolean consultationActivites =
                "/api/activites".equals(request.getRequestURI());
        boolean consultationComptesEnAttente =
                "GET".equalsIgnoreCase(request.getMethod())
                        && "/api/utilisateurs/en-attente".equals(request.getRequestURI());
        boolean telechargementPdf = request.getRequestURI()
                .matches("/api/documents/demandes/[0-9]+/pdf");
        if (!operationEcrit && !consultationActivites
                && !consultationComptesEnAttente && !telechargementPdf) {
            return true;
        }

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
                "{\"message\":\"Authentification requise ou jeton invalide.\"}"
        );
        return false;
    }
}

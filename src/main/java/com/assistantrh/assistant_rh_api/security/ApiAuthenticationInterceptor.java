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
         * Toutes les routes /api/** sont privées par défaut.
         * Les seules exceptions sont les routes explicitement déclarées
         * publiques dans estRoutePublique().
         */
        String authorization = request.getHeader("Authorization");
        if (authorization == null
                || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return refuser(response);
        }

        Integer utilisateurId = bearerTokenService.validerEtLireUtilisateur(
                authorization.substring("Bearer ".length()).trim()
        );
        if (utilisateurId == null) {
            return refuser(response);
        }

        request.setAttribute(USER_ID_ATTRIBUTE, utilisateurId);

        /*
         * Les données et fonctions RH sont réservées aux comptes SPERS
         * actifs (agent ou chef), y compris les routes de consultation
         * qui étaient auparavant accessibles sans authentification.
         */
        String path = cheminApplication(request);
        if (requiertCompteSpersActif(path)
                && !utilisateurService.estAgentSpersActif(utilisateurId)) {
            return interdire(response);
        }

        // Modifier l'organisation (directions, services, postes, grades,
        // statuts) est réservé au chef SPERS ; la lecture reste ouverte
        // à tout compte SPERS actif.
        if (estReferentielOrganisation(path)
                && !estLecture(request)
                && !utilisateurService.estChefActif(utilisateurId)) {
            return interdire(
                    response,
                    "Seul un chef SPERS peut modifier les référentiels d'organisation."
            );
        }

        return true;
    }

    private boolean estReferentielOrganisation(String path) {
        return path.matches("/api/(?:directions|services|postes|grades|statuts)(?:/.*)?");
    }

    private boolean estLecture(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod())
                || "HEAD".equalsIgnoreCase(request.getMethod());
    }

    private boolean requiertCompteSpersActif(String path) {
        return path.matches(
                "/api/(?:"
                        + "employes(?:/.*)?"
                        + "|recherche/agents(?:/.*)?"
                        + "|profil(?:/agent(?:/.*)?)?"
                        + "|referentiels(?:/.*)?"
                        + "|carrieres(?:/.*)?"
                        + "|documents(?:/.*)?"
                        + "|pieces-scannees(?:/.*)?"
                        + "|notifications(?:/.*)?"
                        + "|organisation(?:/.*)?"
                        + "|directions(?:/.*)?"
                        + "|services(?:/.*)?"
                        + "|postes(?:/.*)?"
                        + "|grades(?:/.*)?"
                        + "|statuts(?:/.*)?"
                        + ")"
                )
                || path.matches("/uploads/employes(?:/.*)?");
    }

    private String cheminApplication(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;
    }

    private boolean estRoutePublique(HttpServletRequest request) {
        String path = cheminApplication(request);
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
        return interdire(
                response,
                "Un compte SPERS actif est requis pour accéder à cette ressource."
        );
    }

    /** message est une constante du code, jamais une donnée de la requête. */
    private boolean interdire(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"code\":\"FORBIDDEN\",\"message\":\"" + message + "\"}"
        );
        return false;
    }
}

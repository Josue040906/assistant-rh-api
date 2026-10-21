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

        boolean operationEcrit =
                !"GET".equalsIgnoreCase(request.getMethod())
                        && !"HEAD".equalsIgnoreCase(request.getMethod());
        boolean consultationActivites =
                "/api/activites".equals(request.getRequestURI());
        boolean consultationComptesEnAttente =
                "GET".equalsIgnoreCase(request.getMethod())
                        && "/api/utilisateurs/en-attente".equals(request.getRequestURI());
        boolean notification = request.getRequestURI()
                .matches("/api/notifications(?:/.*)?");
        boolean consultationProfilAgent = request.getRequestURI()
                .matches("/api/profil/agent(?:/.*)?");
        boolean consultationRhProtegee =
                "GET".equalsIgnoreCase(request.getMethod())
                        && (request.getRequestURI().matches(
                                "/api/employes/[0-9]+/diplomes"
                        )
                        || request.getRequestURI().matches(
                                "/api/employes/[0-9]+/affectation"
                        )
                        || request.getRequestURI().matches(
                                "/api/employes/[0-9]+/affectations"
                        )
                        || "/api/referentiels/diplomes/options".equals(
                                request.getRequestURI()
                        )
                        || request.getRequestURI().matches(
                                "/api/employes/[0-9]+/reclassement"
                        )
                        || request.getRequestURI().matches(
                                "/api/carrieres/employes/[0-9]+/reclassement"
                        )
                        || request.getRequestURI().matches(
                                "/api/carrieres/employes/[0-9]+/avancement"
                        )
                        || "/api/referentiels/reclassements".equals(
                                request.getRequestURI()
                        )
                        || "/api/referentiels/reclassements/options".equals(
                                request.getRequestURI()
                        ));
        boolean telechargementPdf = request.getRequestURI()
                .matches("/api/documents/demandes/[0-9]+/pdf");
        boolean rechercheAgent = request.getRequestURI()
                .matches("/api/recherche/agents(?:/approximative|/criteres)?");
        if (!operationEcrit && !consultationActivites
                && !consultationComptesEnAttente && !telechargementPdf
                && !consultationRhProtegee && !rechercheAgent
                && !notification && !consultationProfilAgent) {
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
        if ((rechercheAgent || notification || consultationProfilAgent)
                && !utilisateurService.estAgentSpersActif(utilisateurId)) {
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

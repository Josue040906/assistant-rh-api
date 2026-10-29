package com.assistantrh.assistant_rh_api.security;

import com.assistantrh.assistant_rh_api.service.UtilisateurService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Applies role-based authorization declared on controller methods.
 * ApiAuthenticationInterceptor must run first so the caller identity is trusted.
 */
@Component
public class RoleAuthorizationInterceptor implements HandlerInterceptor {

    private final UtilisateurService utilisateurService;

    public RoleAuthorizationInterceptor(UtilisateurService utilisateurService) {
        this.utilisateurService = utilisateurService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || !(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequiresSpersChef restriction = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(),
                RequiresSpersChef.class
        );
        if (restriction == null) {
            restriction = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(),
                    RequiresSpersChef.class
            );
        }
        if (restriction == null) {
            return true;
        }

        Object identity = request.getAttribute(
                ApiAuthenticationInterceptor.USER_ID_ATTRIBUTE
        );
        if (!(identity instanceof Number userId)) {
            return refuser(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "L'identité authentifiée est requise."
            );
        }

        if (!utilisateurService.estChefActif(userId.intValue())) {
            return refuser(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "FORBIDDEN",
                    "Cette opération est réservée à un chef actif du Service du Personnel."
            );
        }

        return true;
    }

    private boolean refuser(
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}"
        );
        return false;
    }
}

package com.assistantrh.assistant_rh_api.observability;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
public class RequestPerformanceInterceptor implements HandlerInterceptor {
    private static final String REQUEST_ID_ATTRIBUTE =
            RequestPerformanceInterceptor.class.getName() + ".requestId";
    private static final String STARTED_AT_ATTRIBUTE =
            RequestPerformanceInterceptor.class.getName() + ".startedAt";
    private static final String PREVIOUS_MDC_ATTRIBUTE =
            RequestPerformanceInterceptor.class.getName() + ".previousMdc";

    private final PerformanceTelemetry performanceTelemetry;

    public RequestPerformanceInterceptor(
            PerformanceTelemetry performanceTelemetry
    ) {
        this.performanceTelemetry = performanceTelemetry;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        UUID requestId = requestId(request.getHeader("X-Request-ID"));
        String previousRequestId = MDC.get("request_id");
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        request.setAttribute(STARTED_AT_ATTRIBUTE, System.nanoTime());
        if (previousRequestId != null) {
            request.setAttribute(PREVIOUS_MDC_ATTRIBUTE, previousRequestId);
        }
        MDC.put("request_id", requestId.toString());
        response.setHeader("X-Request-ID", requestId.toString());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        UUID requestId = (UUID) request.getAttribute(REQUEST_ID_ATTRIBUTE);
        Long startedAt = (Long) request.getAttribute(STARTED_AT_ATTRIBUTE);
        try {
            if (requestId != null && startedAt != null) {
                performanceTelemetry.apiRequest(
                        requestId,
                        request.getMethod(),
                        response.getStatus(),
                        startedAt
                );
            }
        } finally {
            String previousRequestId =
                    (String) request.getAttribute(PREVIOUS_MDC_ATTRIBUTE);
            if (previousRequestId == null) {
                MDC.remove("request_id");
            } else {
                MDC.put("request_id", previousRequestId);
            }
        }
    }

    private UUID requestId(String supplied) {
        if (supplied != null && supplied.length() == 36) {
            try {
                UUID parsed = UUID.fromString(supplied);
                if (parsed.toString().equalsIgnoreCase(supplied)) {
                    return parsed;
                }
            } catch (IllegalArgumentException ignored) {
                // Replace malformed correlation IDs with a generated UUID.
            }
        }
        return UUID.randomUUID();
    }
}

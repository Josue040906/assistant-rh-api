package com.assistantrh.assistant_rh_api.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestPerformanceInterceptorTests {
    private final RequestPerformanceInterceptor interceptor =
            new RequestPerformanceInterceptor(new PerformanceTelemetry());

    @AfterEach
    void clearMdc() {
        MDC.remove("request_id");
    }

    @Test
    void propagatesValidRequestIdAndRestoresPreviousMdcValue() {
        UUID requestId = UUID.randomUUID();
        MDC.put("request_id", "outer-request");
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/recherche/agents/criteres"
        );
        request.addHeader("X-Request-ID", requestId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(requestId.toString(), response.getHeader("X-Request-ID"));
        assertEquals(requestId.toString(), MDC.get("request_id"));

        interceptor.afterCompletion(request, response, new Object(), null);

        assertEquals("outer-request", MDC.get("request_id"));
    }

    @Test
    void replacesMalformedRequestIdWithGeneratedUuid() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/recherche/agents/approximative"
        );
        request.addHeader("X-Request-ID", "not-a-uuid");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        UUID generated = UUID.fromString(response.getHeader("X-Request-ID"));

        assertNotEquals("not-a-uuid", generated.toString());
        interceptor.afterCompletion(request, response, new Object(), null);
    }
}

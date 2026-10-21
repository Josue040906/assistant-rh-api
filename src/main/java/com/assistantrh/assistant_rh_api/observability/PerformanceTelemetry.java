package com.assistantrh.assistant_rh_api.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PerformanceTelemetry {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(PerformanceTelemetry.class);
    private static final String UNTRACKED_REQUEST_ID = "untracked";

    public void apiRequest(
            UUID requestId,
            String method,
            int status,
            long startedAtNanos
    ) {
        LOGGER.info(
                "rh_perf event=api_request request_id={} method={} status={} "
                        + "duration_us={} outcome={}",
                requestId,
                method,
                status,
                elapsedMicros(startedAtNanos),
                outcome(status)
        );
    }

    public void databaseQuery(
            String operation,
            long startedAtNanos,
            boolean succeeded
    ) {
        String requestId = MDC.get("request_id");
        LOGGER.info(
                "rh_perf event=db_query request_id={} operation={} duration_us={} "
                        + "outcome={}",
                requestId == null ? UNTRACKED_REQUEST_ID : requestId,
                operation,
                elapsedMicros(startedAtNanos),
                succeeded ? "success" : "error"
        );
    }

    private long elapsedMicros(long startedAtNanos) {
        return Math.max(0, (System.nanoTime() - startedAtNanos) / 1_000);
    }

    private String outcome(int status) {
        if (status < 400) {
            return "success";
        }
        return status < 500 ? "client_error" : "server_error";
    }
}

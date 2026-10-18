package com.assistantrh.assistant_rh_api.service;

import org.springframework.http.HttpStatus;

public class ReclassementException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public ReclassementException(
            String code,
            HttpStatus status,
            String message
    ) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

package com.assistantrh.assistant_rh_api.service;

public class TelephoneInvalideException extends RuntimeException {

    public TelephoneInvalideException(String message, Throwable cause) {
        super(message, cause);
    }

    public TelephoneInvalideException(String message) {
        super(message);
    }
}

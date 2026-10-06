package com.assistantrh.assistant_rh_api.service;

public class CinDejaUtiliseException extends RuntimeException {

    public static final String MESSAGE =
            "Ce CIN est déjà utilisé par un autre agent.";

    public CinDejaUtiliseException() {
        super(MESSAGE);
    }
}

package com.assistantrh.assistant_rh_api.service;

public class MatriculeDejaUtiliseException extends RuntimeException {

    public static final String MESSAGE = "Ce matricule existe déjà.";

    public MatriculeDejaUtiliseException() {
        super(MESSAGE);
    }
}

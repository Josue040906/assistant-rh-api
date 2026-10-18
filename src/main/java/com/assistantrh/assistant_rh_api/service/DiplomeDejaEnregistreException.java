package com.assistantrh.assistant_rh_api.service;

public class DiplomeDejaEnregistreException extends RuntimeException {

    public DiplomeDejaEnregistreException() {
        super("Ce diplôme est déjà enregistré pour l'agent à cette date.");
    }
}

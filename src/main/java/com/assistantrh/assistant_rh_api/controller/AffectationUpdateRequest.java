package com.assistantrh.assistant_rh_api.controller;

import java.time.LocalDate;

public record AffectationUpdateRequest(
        Integer acteurId,
        Integer posteId,
        Integer serviceId,
        String lieuTravail,
        LocalDate dateEffet,
        String referenceActe,
        String observation
) {}
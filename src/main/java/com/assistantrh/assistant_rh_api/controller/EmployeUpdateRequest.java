package com.assistantrh.assistant_rh_api.controller;

import java.time.LocalDate;

public record EmployeUpdateRequest(
        String nom,
        String prenom,
        String sexe,
        String adresse,
        String cin,
        String telephone,
        LocalDate dateNaissance,
        String lieuNaissance,
        LocalDate dateEmbauche,
        String lieuTravail,
        String photo
) {}
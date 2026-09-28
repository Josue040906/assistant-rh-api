package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UtilisateurService(
            UtilisateurRepository utilisateurRepository
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public Optional<Integer> trouverIdParEmail(String email) {

        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        return utilisateurRepository.findIdByEmail(
                email.trim()
        );
    }

    public Optional<UtilisateurRepository.UtilisateurLoginData> authentifier(
            String email,
            String password
    ) {

        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        if (password == null || password.isBlank()) {
            return Optional.empty();
        }

        Optional<UtilisateurRepository.UtilisateurLoginData> utilisateur =
                utilisateurRepository.findForLogin(email.trim());

        if (utilisateur.isEmpty()) {
            return Optional.empty();
        }

        UtilisateurRepository.UtilisateurLoginData data =
                utilisateur.get();

        if (data.passwordHash() == null || data.passwordHash().isBlank()) {
            return Optional.empty();
        }

        boolean motDePasseCorrect = passwordEncoder.matches(
                password,
                data.passwordHash()
        );

        if (!motDePasseCorrect) {
            return Optional.empty();
        }

        return Optional.of(data);
    }
}
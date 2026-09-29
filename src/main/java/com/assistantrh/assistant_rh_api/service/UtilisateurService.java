package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class UtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final EmployeRepository employeRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UtilisateurService(
            UtilisateurRepository utilisateurRepository,
            EmployeRepository employeRepository
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.employeRepository = employeRepository;
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

    public Optional<Map<String, Object>> authentifier(
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

        Map<String, Object> resultat = new HashMap<>();

        resultat.put("userId", data.id());
        resultat.put("email", data.email());

        Optional<Map<String, Object>> employe =
                employeRepository.findByUserId(data.id());

        if (employe.isPresent()) {
            Map<String, Object> agent = employe.get();

            resultat.put("employeId", agent.get("id"));
            resultat.put("matricule", agent.get("matricule"));
            resultat.put("nom", agent.get("nom"));
            resultat.put("prenom", agent.get("prenom"));
            resultat.put("photo", agent.get("photo"));
            resultat.put("poste", agent.get("poste"));
            resultat.put("codeService", agent.get("code_service"));
            resultat.put("service", agent.get("service"));
            resultat.put("directionId", agent.get("direction_id"));
            resultat.put("direction", agent.get("direction"));
        }

        return Optional.of(resultat);
    }

    @Transactional
    public Optional<Map<String, Object>> inscrire(
            String matricule,
            String email,
            String motDePasse
    ) {

        if (matricule == null || matricule.isBlank()) {
            return Optional.empty();
        }

        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        if (motDePasse == null || motDePasse.isBlank()) {
            return Optional.empty();
        }

        String matriculeNormalise = matricule.trim();
        String emailNormalise = email.trim();

        Optional<Map<String, Object>> employe =
                employeRepository.findByMatricule(matriculeNormalise);

        if (employe.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> agent = employe.get();

        Object userIdExistant = agent.get("user_id");

        if (userIdExistant != null) {
            return Optional.empty();
        }

        if (utilisateurRepository.existsByEmail(emailNormalise)) {
            return Optional.empty();
        }

        String passwordHash =
                passwordEncoder.encode(motDePasse);

        int utilisateurId =
                utilisateurRepository.creerUtilisateur(
                        emailNormalise,
                        passwordHash
                );

        boolean associe =
                employeRepository.associerUtilisateur(
                        (Integer) agent.get("id"),
                        utilisateurId
                );

        if (!associe) {
            return Optional.empty();
        }

        Map<String, Object> resultat = new HashMap<>();

        resultat.put("userId", utilisateurId);
        resultat.put("employeId", agent.get("id"));
        resultat.put("matricule", agent.get("matricule"));
        resultat.put("nom", agent.get("nom"));
        resultat.put("prenom", agent.get("prenom"));
        resultat.put("email", emailNormalise);

        return Optional.of(resultat);
    }
    public boolean changerMotDePasse(
            int utilisateurId,
            String ancienMotDePasse,
            String nouveauMotDePasse
    ) {

        if (ancienMotDePasse == null || ancienMotDePasse.isBlank()) {
            return false;
        }

        if (nouveauMotDePasse == null || nouveauMotDePasse.isBlank()) {
            return false;
        }

        Optional<UtilisateurRepository.UtilisateurLoginData> utilisateur =
                utilisateurRepository.findForLoginParId(utilisateurId);

        if (utilisateur.isEmpty()) {
            return false;
        }

        UtilisateurRepository.UtilisateurLoginData data =
                utilisateur.get();

        if (data.passwordHash() == null || data.passwordHash().isBlank()) {
            return false;
        }

        boolean ancienMotDePasseCorrect = passwordEncoder.matches(
                ancienMotDePasse,
                data.passwordHash()
        );

        if (!ancienMotDePasseCorrect) {
            return false;
        }

        String nouveauPasswordHash =
                passwordEncoder.encode(nouveauMotDePasse);

        return utilisateurRepository.mettreAJourMotDePasse(
                utilisateurId,
                nouveauPasswordHash
        );
    }
}
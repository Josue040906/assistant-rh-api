package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.repository.EmployeRepository;
import com.assistantrh.assistant_rh_api.repository.UtilisateurRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;

@Service
public class UtilisateurService {

    /*
     * Service utilisé par SYGPERS.
     *
     * Dans notre base actuelle :
     * 2 = SERVICE DE LA GESTION DES EFFECTIFS DES AGENTS DE L'ETAT
     */
    private static final int SERVICE_SYGPERS_ID = 2;

    private final UtilisateurRepository utilisateurRepository;
    private final EmployeRepository employeRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final ActiviteService activiteService;

    public UtilisateurService(
            UtilisateurRepository utilisateurRepository,
            EmployeRepository employeRepository,
            ActiviteService activiteService
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.employeRepository = employeRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
        this.activiteService = activiteService;
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

        /*
         * Le compte doit être actif pour pouvoir se connecter.
         *
         * Les comptes EN_ATTENTE, REFUSE ou DESACTIVE
         * ne peuvent pas accéder à SYGPERS.
         */
        if (!"ACTIF".equalsIgnoreCase(data.statutCompte())) {
            return Optional.empty();
        }

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
        resultat.put("role", data.role());
        resultat.put("statutCompte", data.statutCompte());

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

        /*
         * Seuls les agents du service SYGPERS
         * peuvent demander la création d'un compte.
         */
        Object serviceId = agent.get("service_id");

        if (!(serviceId instanceof Number)
                || ((Number) serviceId).intValue() != SERVICE_SYGPERS_ID) {
            return Optional.empty();
        }

        Object userIdExistant = agent.get("user_id");

        if (userIdExistant != null) {
            return Optional.empty();
        }

        if (utilisateurRepository.existsByEmail(emailNormalise)) {
            return Optional.empty();
        }

        String passwordHash =
                passwordEncoder.encode(motDePasse);

        /*
         * Le repository crée automatiquement :
         *
         * role = SPERS_AGENT
         * statut_compte = EN_ATTENTE
         */
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

        /*
         * L'inscription est une action système.
         * Il n'y a pas encore d'acteur authentifié.
         *
         * acteur_id peut donc rester NULL dans activite.
         */
        activiteService.enregistrer(
                null,
                (Integer) agent.get("id"),
                "CREATION_COMPTE",
                "Demande de création de compte SYGPERS pour l'agent "
                        + agent.get("matricule")
                        + " - compte placé en attente de validation."
        );

        Map<String, Object> resultat = new HashMap<>();

        resultat.put("userId", utilisateurId);
        resultat.put("employeId", agent.get("id"));
        resultat.put("matricule", agent.get("matricule"));
        resultat.put("nom", agent.get("nom"));
        resultat.put("prenom", agent.get("prenom"));
        resultat.put("email", emailNormalise);
        resultat.put("role", "SPERS_AGENT");
        resultat.put("statutCompte", "EN_ATTENTE");

        return Optional.of(resultat);
    }

    public boolean estChefActif(int utilisateurId) {
        return utilisateurRepository.estChefActif(utilisateurId);
    }

    public boolean estAgentSpersActif(int utilisateurId) {
        return utilisateurRepository.estAgentSpersActif(utilisateurId);
    }

    public List<Map<String, Object>> listerComptesEnAttente(
            int acteurId
    ) {

        if (!utilisateurRepository.estChefActif(acteurId)) {
            return List.of();
        }

        return utilisateurRepository.findComptesEnAttente();
    }

    @Transactional
    public boolean approuverCompte(
            int acteurId,
            int utilisateurId
    ) {

        if (!utilisateurRepository.estChefActif(acteurId)) {
            return false;
        }

        Optional<UtilisateurRepository.UtilisateurLoginData> utilisateur =
                utilisateurRepository.findForLoginParId(utilisateurId);

        if (utilisateur.isEmpty()) {
            return false;
        }

        UtilisateurRepository.UtilisateurLoginData data =
                utilisateur.get();

        if (!"EN_ATTENTE".equalsIgnoreCase(data.statutCompte())) {
            return false;
        }

        boolean modifie =
                utilisateurRepository.mettreAJourStatut(
                        utilisateurId,
                        "ACTIF"
                );

        if (!modifie) {
            return false;
        }

        Optional<Map<String, Object>> employe =
                employeRepository.findByUserId(utilisateurId);

        Integer employeId = employe
                .map(agent -> (Integer) agent.get("id"))
                .orElse(null);

        activiteService.enregistrer(
                acteurId,
                employeId,
                "APPROBATION_COMPTE",
                "Approbation du compte SYGPERS "
                        + data.email()
        );

        return true;
    }

    @Transactional
    public boolean refuserCompte(
            int acteurId,
            int utilisateurId
    ) {

        if (!utilisateurRepository.estChefActif(acteurId)) {
            return false;
        }

        Optional<UtilisateurRepository.UtilisateurLoginData> utilisateur =
                utilisateurRepository.findForLoginParId(utilisateurId);

        if (utilisateur.isEmpty()) {
            return false;
        }

        UtilisateurRepository.UtilisateurLoginData data =
                utilisateur.get();

        if (!"EN_ATTENTE".equalsIgnoreCase(data.statutCompte())) {
            return false;
        }

        boolean modifie =
                utilisateurRepository.mettreAJourStatut(
                        utilisateurId,
                        "REFUSE"
                );

        if (!modifie) {
            return false;
        }

        Optional<Map<String, Object>> employe =
                employeRepository.findByUserId(utilisateurId);

        Integer employeId = employe
                .map(agent -> (Integer) agent.get("id"))
                .orElse(null);

        activiteService.enregistrer(
                acteurId,
                employeId,
                "REFUS_COMPTE",
                "Refus du compte SYGPERS "
                        + data.email()
        );

        return true;
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
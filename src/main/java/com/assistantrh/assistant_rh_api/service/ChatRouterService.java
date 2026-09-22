package com.assistantrh.assistant_rh_api.service;

import com.assistantrh.assistant_rh_api.controller.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ChatRouterService {

    private final EmployeService employeService;
    private final GeminiService geminiService;

    public ChatRouterService(
            EmployeService employeService,
            GeminiService geminiService
    ) {
        this.employeService = employeService;
        this.geminiService = geminiService;
    }

    public ChatResponse router(String message) {

        if (message == null || message.isBlank()) {
            return new ChatResponse(
                    "text",
                    "Veuillez saisir une demande."
            );
        }

        String demande = message.trim().toLowerCase();

        /*
         * ==========================================
         * 1. LISTE DES EMPLOYÉS
         * ==========================================
         */

        if (estDemandeListeEmployes(demande)) {

            List<Map<String, Object>> employes =
                    employeService.getAllEmployes();

            Map<String, Object> data = new HashMap<>();

            data.put(
                    "message",
                    "Voici la liste des employés."
            );

            data.put(
                    "employees",
                    employes
            );

            return new ChatResponse(
                    "employee_list",
                    data
            );
        }

        /*
         * ==========================================
         * 2. PROFIL D'UN EMPLOYÉ
         * ==========================================
         */

        String rechercheProfil =
                extraireRechercheProfil(demande, message);

        if (rechercheProfil != null) {

            Optional<Map<String, Object>> profil =
                    employeService.rechercherProfilEmploye(
                            rechercheProfil
                    );

            if (profil.isPresent()) {

                Map<String, Object> data = new HashMap<>();

                data.put(
                        "message",
                        "Voici le profil de l'employé demandé."
                );

                data.put(
                        "employee",
                        profil.get()
                );

                return new ChatResponse(
                        "employee_profile",
                        data
                );
            }

            Map<String, Object> data = new HashMap<>();

            data.put(
                    "message",
                    "Je n'ai trouvé aucun employé correspondant à votre recherche."
            );

            data.put(
                    "query",
                    rechercheProfil
            );

            return new ChatResponse(
                    "text",
                    data
            );
        }

        /*
         * ==========================================
         * 3. RECHERCHE D'EMPLOYÉS
         * ==========================================
         */

        String recherche =
                extraireRechercheEmploye(demande, message);

        if (recherche != null) {

            List<Map<String, Object>> employes =
                    employeService.rechercherEmployes(
                            recherche
                    );

            Map<String, Object> data = new HashMap<>();

            if (employes.isEmpty()) {

                data.put(
                        "message",
                        "Je n'ai trouvé aucun employé correspondant à votre recherche."
                );

                data.put(
                        "query",
                        recherche
                );

                return new ChatResponse(
                        "text",
                        data
                );
            }

            data.put(
                    "message",
                    "Voici les employés correspondant à votre recherche."
            );

            data.put(
                    "employees",
                    employes
            );

            data.put(
                    "query",
                    recherche
            );

            return new ChatResponse(
                    "employee_list",
                    data
            );
        }

        /*
         * ==========================================
         * 4. GEMINI COMME SECOURS
         * ==========================================
         */

        return geminiService.envoyerMessage(message);
    }

    private boolean estDemandeListeEmployes(String demande) {

        return demande.equals("liste des employés")
                || demande.equals("liste des employes")
                || demande.equals("affiche les employés")
                || demande.equals("affiche les employes")
                || demande.equals("donne-moi les employés")
                || demande.equals("donne-moi les employes")
                || demande.equals("donne moi les employés")
                || demande.equals("donne moi les employes");
    }

    private String extraireRechercheProfil(
            String demande,
            String messageOriginal
    ) {

        String[] prefixes = {
                "profil de ",
                "profile de ",
                "profil ",
                "profile ",
                "fiche de ",
                "fiche "
        };

        for (String prefix : prefixes) {

            if (demande.startsWith(prefix)) {

                String recherche =
                        messageOriginal.substring(prefix.length()).trim();

                if (!recherche.isBlank()) {
                    return recherche;
                }
            }
        }

        return null;
    }

    private String extraireRechercheEmploye(
            String demande,
            String messageOriginal
    ) {

        String[] prefixes = {
                "cherche les employés qui sont ",
                "cherche les employes qui sont ",
                "cherche les employés ",
                "cherche les employes ",
                "cherche un employé ",
                "cherche un employe ",
                "cherche l'employé ",
                "cherche l'employe ",
                "cherche ",
                "recherche ",
                "trouve ",
                "affiche les employés ",
                "affiche les employes "
        };

        for (String prefix : prefixes) {

            if (demande.startsWith(prefix)) {

                String recherche =
                        messageOriginal.substring(prefix.length()).trim();

                if (!recherche.isBlank()) {
                    return recherche;
                }
            }
        }

        return null;
    }
}
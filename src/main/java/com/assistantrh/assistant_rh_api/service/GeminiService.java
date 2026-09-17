
        package com.assistantrh.assistant_rh_api.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.genai.Client;
import com.google.genai.Chat;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;

@Service
public class GeminiService {

    private final Client client;
    private final EmployeService employeService;

    public GeminiService(
            @Value("${gemini.api.key:}") String apiKey,
            EmployeService employeService
    ) {

        if (apiKey != null && !apiKey.isBlank()) {

            this.client = Client.builder()
                    .apiKey(apiKey)
                    .build();

        } else {

            this.client = new Client();
        }

        this.employeService = employeService;
    }

    public String envoyerMessage(String messageUtilisateur) {

        try {

            // ============================================================
            // 1. Schéma du paramètre "query" pour searchEmployees
            // ============================================================

            Schema querySchema = Schema.builder()
                    .type("STRING")
                    .description(
                            "Texte utilisé pour rechercher des employés. "
                                    + "La recherche peut porter sur le nom, le prénom, "
                                    + "le matricule, le poste, le code d'un service, "
                                    + "le nom d'un service ou le nom d'une direction."
                    )
                    .build();

            // ============================================================
            // 2. Schéma des paramètres de searchEmployees
            // ============================================================

            Schema parametersSchema = Schema.builder()
                    .type("OBJECT")
                    .properties(Map.of(
                            "query", querySchema
                    ))
                    .required(List.of("query"))
                    .build();

            // ============================================================
            // 3. Déclaration de searchEmployees
            // ============================================================

            FunctionDeclaration searchEmployees =
                    FunctionDeclaration.builder()
                            .name("searchEmployees")
                            .description(
                                    "Recherche des employés dans la base de données RH. "
                                            + "Utilise cette fonction lorsqu'un utilisateur demande "
                                            + "de rechercher, trouver, afficher ou lister des employés. "
                                            + "La recherche peut être effectuée à partir d'un nom, "
                                            + "d'un prénom, d'un matricule, d'un poste, d'un service, "
                                            + "d'un code service ou d'une direction. "
                                            + "Les résultats retournés par cette fonction proviennent "
                                            + "directement de la base de données."
                            )
                            .parameters(parametersSchema)
                            .build();

            // ============================================================
            // 4. Schéma du paramètre "query" pour getEmployeeProfile
            // ============================================================

            Schema profileQuerySchema = Schema.builder()
                    .type("STRING")
                    .description(
                            "Nom, prénom ou matricule de l'employé "
                                    + "dont le profil est recherché."
                    )
                    .build();

            // ============================================================
            // 5. Schéma des paramètres de getEmployeeProfile
            // ============================================================

            Schema profileParametersSchema = Schema.builder()
                    .type("OBJECT")
                    .properties(Map.of(
                            "query", profileQuerySchema
                    ))
                    .required(List.of("query"))
                    .build();

            // ============================================================
            // 6. Déclaration de getEmployeeProfile
            // ============================================================

            FunctionDeclaration getEmployeeProfile =
                    FunctionDeclaration.builder()
                            .name("getEmployeeProfile")
                            .description(
                                    "Récupère les informations détaillées d'un employé "
                                            + "à partir de son nom, prénom ou matricule. "
                                            + "Utilise cette fonction lorsqu'un utilisateur demande "
                                            + "le profil, les informations ou les détails "
                                            + "d'un employé précis. "
                                            + "Les informations retournées proviennent directement "
                                            + "de la base de données."
                            )
                            .parameters(profileParametersSchema)
                            .build();

            // ============================================================
            // 7. Déclaration du Tool
            // ============================================================

            Tool tool = Tool.builder()
                    .functionDeclarations(List.of(
                            searchEmployees,
                            getEmployeeProfile
                    ))
                    .build();

            // ============================================================
            // 8. Instructions générales pour Gemini
            // ============================================================

            String systemInstruction = """
                    Tu es un assistant intelligent spécialisé dans la gestion
                    des ressources humaines.

                    Tu aides l'utilisateur à consulter les informations RH
                    disponibles dans la base de données.

                    RÈGLES IMPORTANTES :

                    1. Les informations concernant les employés doivent provenir
                       des fonctions disponibles et donc de la base de données.

                    2. N'invente jamais le nom, le matricule, le poste, le service,
                       la direction ou toute autre information concernant un employé.

                    3. Lorsqu'un utilisateur demande de rechercher ou de lister
                       des employés, utilise searchEmployees.

                    4. Lorsqu'un utilisateur demande les informations détaillées
                       d'un employé précis, utilise getEmployeeProfile.

                    5. Un service peut ne pas avoir de direction directement
                       rattachée. Dans ce cas, indique simplement que la direction
                       n'est pas renseignée.

                    6. Réponds en français de manière claire, concise et naturelle.

                    7. Ne présente jamais une information comme provenant de la
                       base de données si elle n'a pas été retournée par une fonction.
                    """;

            // ============================================================
            // 9. Configuration Gemini
            // ============================================================

            GenerateContentConfig config =
                    GenerateContentConfig.builder()
                            .tools(List.of(tool))
                            .systemInstruction(
                                    Content.fromParts(
                                            Part.fromText(systemInstruction)
                                    )
                            )
                            .build();

            // ============================================================
            // 10. Création du Chat
            // ============================================================

            Chat chat = client.chats.create(
                    "gemini-3.6-flash",
                    config
            );

            // ============================================================
            // 11. Premier message utilisateur
            // ============================================================

            GenerateContentResponse response =
                    chat.sendMessage(messageUtilisateur);

            // ============================================================
            // 12. Recherche des FunctionCall
            // ============================================================

            List<FunctionCall> functionCalls = response.parts()
                    .stream()
                    .map(Part::functionCall)
                    .flatMap(Optional::stream)
                    .toList();

            // ============================================================
            // 13. Aucun appel de fonction
            // ============================================================

            if (functionCalls.isEmpty()) {

                return response.text();
            }

            // ============================================================
            // 14. Traitement du premier FunctionCall
            // ============================================================

            FunctionCall functionCall = functionCalls.get(0);

            String functionName = functionCall.name()
                    .orElse("");

            Map<String, Object> arguments = functionCall.args()
                    .orElse(Map.of());

            // ============================================================
            // 15. Affichage du FunctionCall pour le débogage
            // ============================================================

            System.out.println();
            System.out.println("===== FUNCTION CALL =====");
            System.out.println("Nom       : " + functionName);
            System.out.println("Arguments : " + arguments);
            System.out.println(
                    "ID        : " + functionCall.id().orElse("")
            );
            System.out.println("=========================");
            System.out.println();

            // ============================================================
            // 16. Exécution de searchEmployees
            // ============================================================

            if ("searchEmployees".equals(functionName)) {

                String query = String.valueOf(
                        arguments.getOrDefault("query", "")
                );

                System.out.println(
                        "Recherche des employés avec : " + query
                );

                List<Map<String, Object>> employees =
                        employeService.rechercherEmployes(query);

                System.out.println(
                        "Nombre d'employés trouvés : "
                                + employees.size()
                );

                System.out.println(
                        "Résultats : " + employees
                );

                return envoyerResultatFonction(
                        chat,
                        functionCall.id().orElse(""),
                        functionName,
                        employees
                );
            }

            // ============================================================
            // 17. Exécution de getEmployeeProfile
            // ============================================================

            if ("getEmployeeProfile".equals(functionName)) {

                String query = String.valueOf(
                        arguments.getOrDefault("query", "")
                );

                System.out.println(
                        "Recherche du profil de l'employé : " + query
                );

                Optional<Map<String, Object>> employee =
                        employeService.rechercherProfilEmploye(query);

                System.out.println(
                        "Profil trouvé : " + employee
                );

                return envoyerResultatFonction(
                        chat,
                        functionCall.id().orElse(""),
                        functionName,
                        employee.orElse(Map.of())
                );
            }

            // ============================================================
            // 18. Fonction inconnue
            // ============================================================

            return response.text();

        } catch (Exception e) {

            e.printStackTrace();

            return "Erreur lors de la communication avec l'API Gemini : "
                    + e.getMessage();
        }
    }

    // ====================================================================
    // Méthode commune pour envoyer le résultat d'une fonction à Gemini
    // ====================================================================

    private String envoyerResultatFonction(
            Chat chat,
            String functionCallId,
            String functionName,
            Object output
    ) {

        // ================================================================
        // 1. Création de la réponse de la fonction
        // ================================================================

        FunctionResponse functionResponse =
                FunctionResponse.builder()
                        .id(functionCallId)
                        .name(functionName)
                        .response(Map.of(
                                "output", output
                        ))
                        .build();

        // ================================================================
        // 2. Création du Part contenant le résultat
        // ================================================================

        Part responsePart = Part.builder()
                .functionResponse(functionResponse)
                .build();

        // ================================================================
        // 3. Création du Content contenant le résultat
        // ================================================================

        Content toolResult = Content.fromParts(responsePart);

        // ================================================================
        // 4. Affichage pour le débogage
        // ================================================================

        System.out.println();
        System.out.println(
                "===== ENVOI DU RESULTAT A GEMINI ====="
        );
        System.out.println(
                "FunctionResponse : " + functionResponse
        );
        System.out.println(
                "======================================="
        );
        System.out.println();

        // ================================================================
        // 5. Configuration pour la réponse finale
        // ================================================================

        GenerateContentConfig finalConfig =
                GenerateContentConfig.builder()
                        .build();

        // ================================================================
        // 6. Envoi du résultat au même Chat
        // ================================================================

        GenerateContentResponse finalResponse =
                chat.sendMessage(
                        toolResult,
                        finalConfig
                );

        // ================================================================
        // 7. Affichage de la réponse finale
        // ================================================================

        System.out.println();
        System.out.println(
                "===== REPONSE FINALE GEMINI ====="
        );

        System.out.println(finalResponse);

        System.out.println(
                "===== TEXTE FINAL ====="
        );

        System.out.println(
                finalResponse.text()
        );

        System.out.println(
                "================================="
        );

        // ================================================================
        // 8. Retour du texte final
        // ================================================================

        return finalResponse.text();
    }
}


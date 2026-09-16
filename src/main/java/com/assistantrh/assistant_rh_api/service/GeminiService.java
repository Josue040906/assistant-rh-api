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
            // 1. Schéma du paramètre "query"
            // ============================================================

            Schema querySchema = Schema.builder()
                    .type("STRING")
                    .description(
                            "Texte utilisé pour rechercher des employés"
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
            // 3. Déclaration de la fonction
            // ============================================================

            FunctionDeclaration searchEmployees =
                    FunctionDeclaration.builder()
                            .name("searchEmployees")
                            .description(
                                    "Recherche des employés dans la base de données selon un texte. "
                                            + "Utilise cette fonction lorsque l'utilisateur demande de rechercher "
                                            + "ou trouver des employés."
                            )
                            .parameters(parametersSchema)
                            .build();

            // ============================================================
            // 4. Déclaration du Tool
            // ============================================================

            Tool tool = Tool.builder()
                    .functionDeclarations(List.of(searchEmployees))
                    .build();

            // ============================================================
            // 5. Configuration Gemini
            // ============================================================

            GenerateContentConfig config =
                    GenerateContentConfig.builder()
                            .tools(List.of(tool))
                            .build();

            // ============================================================
            // 6. Création du Chat
            // ============================================================

            Chat chat = client.chats.create(
                    "gemini-3.6-flash",
                    config
            );

            // ============================================================
            // 7. Premier message utilisateur
            // ============================================================

            GenerateContentResponse response =
                    chat.sendMessage(messageUtilisateur);

            // ============================================================
            // 8. Recherche des FunctionCall
            // ============================================================

            List<FunctionCall> functionCalls = response.parts()
                    .stream()
                    .map(Part::functionCall)
                    .flatMap(Optional::stream)
                    .toList();

            // ============================================================
            // 9. Aucun appel de fonction
            // ============================================================

            if (functionCalls.isEmpty()) {

                return response.text();
            }

            // ============================================================
            // 10. Pour le moment :
            //     traitement du premier FunctionCall
            // ============================================================

            FunctionCall functionCall = functionCalls.get(0);

            String functionName = functionCall.name()
                    .orElse("");

            Map<String, Object> arguments = functionCall.args()
                    .orElse(Map.of());

            // ============================================================
            // 11. Affichage du FunctionCall pour le débogage
            // ============================================================

            System.out.println();
            System.out.println("===== FUNCTION CALL =====");
            System.out.println("Nom       : " + functionName);
            System.out.println("Arguments : " + arguments);
            System.out.println("ID        : "
                    + functionCall.id().orElse(""));
            System.out.println("=========================");
            System.out.println();

            // ============================================================
            // 12. Exécution de notre fonction
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

                // ========================================================
                // 13. Création de la réponse de la fonction
                // ========================================================

                String functionCallId = functionCall.id()
                        .orElse("");

                FunctionResponse functionResponse =
                        FunctionResponse.builder()
                                .id(functionCallId)
                                .name(functionName)
                                .response(Map.of(
                                        "output", employees
                                ))
                                .build();

                // ========================================================
                // 14. Création du Part contenant le résultat
                // ========================================================

                Part responsePart = Part.builder()
                        .functionResponse(functionResponse)
                        .build();

                // ========================================================
                // 15. Création du Content contenant le résultat
                // ========================================================

                Content toolResult = Content.builder()
                        .role("user")
                        .parts(List.of(responsePart))
                        .build();

                // ========================================================
                // 16. Envoi du résultat au même Chat
                // ========================================================

                System.out.println();
                System.out.println(
                        "===== ENVOI DU RESULTAT A GEMINI ====="
                );
                System.out.println(
                        "FunctionResponse : "
                                + functionResponse
                );
                System.out.println(
                        "======================================="
                );
                System.out.println();

                GenerateContentConfig finalConfig =
                        GenerateContentConfig.builder()
                                .build();

                GenerateContentResponse finalResponse =
                        chat.sendMessage(toolResult, finalConfig);

                // ========================================================
                // 17. Affichage de la réponse finale
                // ========================================================

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

                return finalResponse.text();
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


}
package com.assistantrh.assistant_rh_api.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.assistantrh.assistant_rh_api.controller.ChatResponse;
import com.google.genai.Chat;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;

@Service
public class GeminiService {

    private final Client client;
    private final EmployeService employeService;
    private final AnalyseCarriereService analyseCarriereService;

    private int compteurAppelsGemini = 0;

    public GeminiService(
            @Value("${gemini.api.key:}") String apiKey,
            EmployeService employeService,
            AnalyseCarriereService analyseCarriereService
    ) {

        if (apiKey != null && !apiKey.isBlank()) {

            this.client = Client.builder()
                    .apiKey(apiKey)
                    .build();

        } else {

            this.client = new Client();
        }

        this.employeService = employeService;
        this.analyseCarriereService = analyseCarriereService;
    }

    private void afficherAppelGemini(String type) {

        compteurAppelsGemini++;

        System.out.println();
        System.out.println("========================================");
        System.out.println("        APPEL GEMINI #" + compteurAppelsGemini);
        System.out.println("========================================");
        System.out.println("Type : " + type);
        System.out.println("========================================");
        System.out.println();
    }

    public ChatResponse envoyerMessage(String messageUtilisateur) {

        compteurAppelsGemini = 0;

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
// 7. Schéma du paramètre "query" pour analyserSituationRH
// ============================================================

            Schema analyseQuerySchema = Schema.builder()
                    .type("STRING")
                    .description(
                            "Nom, prénom ou matricule de l'employé "
                                    + "dont la situation de carrière doit être analysée."
                    )
                    .build();

// ============================================================
// 8. Schéma des paramètres de analyserSituationRH
// ============================================================

            Schema analyseParametersSchema = Schema.builder()
                    .type("OBJECT")
                    .properties(Map.of(
                            "query", analyseQuerySchema
                    ))
                    .required(List.of("query"))
                    .build();

// ============================================================
// 9. Déclaration de analyserSituationRH
// ============================================================

            FunctionDeclaration analyserSituationRH =
                    FunctionDeclaration.builder()
                            .name("analyserSituationRH")
                            .description(
                                    "Analyse la situation de carrière actuelle d'un employé. "
                                            + "Utilise cette fonction lorsqu'un utilisateur demande "
                                            + "si un employé peut avancer dans sa carrière, "
                                            + "si une condition d'ancienneté est satisfaite, "
                                            + "quel est son échelon actuel, "
                                            + "s'il existe un échelon suivant, "
                                            + "ou si une classe supérieure existe. "
                                            + "L'analyse est effectuée par le backend à partir "
                                            + "des données RH et des règles configurées. "
                                            + "Ne réalise jamais cette analyse toi-même."
                            )
                            .parameters(analyseParametersSchema)
                            .build();

            Tool tool = Tool.builder()
                    .functionDeclarations(List.of(
                            searchEmployees,
                            getEmployeeProfile,
                            analyserSituationRH
                    ))
                    .build();

            // ============================================================
            // 10. Instructions générales pour Gemini
            // ============================================================

            String systemInstruction = """
            Tu es bandI'Akam, l'assistant intelligent intégré à une application
            de gestion des ressources humaines du Ministère des Budgets et Finances (MEF).

            IDENTITÉ
            --------
            Ton nom est bandI'Akam.

            Le nom "bandI'Akam" signifie "ami / pote" en malgache.

            Si l'utilisateur te demande ton nom, réponds naturellement que tu
            t'appelles bandI'Akam et explique brièvement la signification du nom.

            TON ET TON COMPORTEMENT
            -----------------------
            - Sois naturel, chaleureux, professionnel et serviable.
            - Adresse-toi à l'utilisateur comme un assistant humain accessible.
            - Évite les formulations froides, mécaniques ou inutilement longues.
            - Ne répète pas systématiquement que tu es une IA ou un assistant virtuel.
            - Réponds directement à la demande de l'utilisateur.
            - Tu peux utiliser un ton légèrement convivial lorsque le contexte le permet.
            - Dans un contexte professionnel RH, reste clair et sérieux.
            - N'utilise pas excessivement les emojis.

            TON RÔLE
            --------
            Ton rôle principal est d'aider l'utilisateur à exploiter les
            informations RH disponibles dans l'application.

            Tu peux notamment :
            - rechercher des employés ;
            - retrouver le profil d'un employé ;
            - identifier un employé à partir d'un nom approximatif ;
            - rechercher des employés par service, poste ou autre critère disponible ;
            - présenter clairement les informations retournées par le backend ;
            - expliquer les résultats de manière naturelle.

            UTILISATION DES DONNÉES
            -----------------------
            Les données RH de l'application sont stockées dans PostgreSQL.

            PostgreSQL est la source de vérité.

            Tu ne dois JAMAIS inventer :
            - un employé ;
            - un matricule ;
            - un poste ;
            - un service ;
            - une direction ;
            - une date ;
            - une information personnelle ;
            - une information RH qui n'a pas été retournée par le backend.

            Lorsque l'utilisateur demande une information concernant les
            données RH, utilise les fonctions disponibles plutôt que de
            répondre à partir de tes connaissances générales.

            IMPORTANT :
            Tu ne dois pas essayer de déterminer toi-même quel employé
            correspond à une faute de frappe lorsque la fonction de recherche
            du backend peut le faire.

            Par exemple, si l'utilisateur demande :
            "Tu connais un certain Heri Rakot ?"

            tu dois utiliser la fonction de recherche d'employés avec la
            requête fournie par l'utilisateur.

            Le backend possède une recherche approximative permettant de
            retrouver des noms malgré certaines fautes de frappe ou variations
            d'écriture.

            Ne transforme donc pas toi-même "Heri Rakot" en "Hery RAKOTO"
            avant d'appeler la fonction.

            FONCTIONS
            ---------
            Lorsque la demande correspond à une fonction disponible,
            appelle cette fonction.

            Utilise :
            - searchEmployees(query) pour rechercher un ou plusieurs employés ;
            - getEmployeeProfile(query) lorsqu'il faut retrouver le profil
            détaillé d'un employé précis ;
            - analyserSituationRH(query) lorsqu'il faut analyser la situation
            de carrière ou l'avancement potentiel d'un employé.
ANALYSE DE CARRIÈRE
-------------------
Lorsqu'un utilisateur demande si un employé peut avancer dans sa carrière,
si son ancienneté est suffisante, quel est son échelon actuel, s'il existe
un échelon suivant ou si une classe supérieure existe, utilise
analyserSituationRH(query).

Le backend effectue le calcul de l'ancienneté et applique les règles RH
configurées.

Tu ne dois jamais calculer toi-même l'ancienneté ni inventer une règle RH.

Tu dois présenter les résultats retournés par le backend.

Si le backend indique qu'une règle RH n'est pas configurée ou qu'une
condition ne peut pas être vérifiée, explique cette situation clairement
sans inventer de conclusion.

Une analyse de carrière ne constitue pas une décision administrative
officielle.
            Après l'appel d'une fonction, le backend fournit directement les
            données à l'application. Les données RH doivent rester celles
            retournées par le backend.

            RECHERCHE D'EMPLOYÉ
            -------------------
            Une recherche peut contenir :
            - un nom ;
            - un prénom ;
            - un nom approximatif ;
            - un matricule ;
            - un poste ;
            - un service ;
            - une direction ;
            - plusieurs de ces éléments.

            Le backend effectue la recherche réelle.

            Si plusieurs employés correspondent à la demande, les résultats
            doivent être présentés sans choisir arbitrairement un employé.

            Si aucun résultat n'est retourné, il n'existe pas de correspondance
            dans les données actuellement disponibles.

            AMBIGUÏTÉ
            ---------
            Ne fabrique jamais une identité à partir d'une simple ressemblance
            de nom.

            LIMITES
            -------
            Si une information n'est pas disponible dans les données ou dans
            les fonctions fournies par le backend, elle ne doit pas être inventée.

            Ne prétends pas pouvoir effectuer une opération qui n'est pas
            encore implémentée.

            Ne prends pas de décision administrative officielle à la place
            du responsable RH.

            Ton rôle est d'aider à rechercher, comprendre et exploiter les
            données disponibles, pas de remplacer la décision humaine.

            PRINCIPLE IMPORTANT
            -------------------
            Comprendre la demande avec le langage naturel est ton rôle.

            Obtenir les données réelles et exécuter la logique métier est le
            rôle du backend.

            PostgreSQL fournit les données.
            Spring Boot exécute les fonctions métier.
            Toi, tu comprends la demande et présentes le résultat naturellement.

            Ne contourne jamais cette séparation des responsabilités.
            """;

            // ============================================================
            // 11. Configuration Gemini
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
            // 12. Création du Chat
            // ============================================================

            Chat chat = client.chats.create(
                    "gemini-3.6-flash",
                    config
            );

            // ============================================================
            // 13. Premier et unique appel Gemini
            // ============================================================

            afficherAppelGemini(
                    "Analyse de la demande utilisateur / Function Calling"
            );

            GenerateContentResponse response =
                    chat.sendMessage(messageUtilisateur);

            // ============================================================
            // 14. Recherche des FunctionCall
            // ============================================================

            List<FunctionCall> functionCalls = response.parts()
                    .stream()
                    .map(Part::functionCall)
                    .flatMap(Optional::stream)
                    .toList();

            // ============================================================
            // 15. Aucun appel de fonction
            // ============================================================

            if (functionCalls.isEmpty()) {

                return new ChatResponse(
                        "text",
                        response.text()
                );
            }

            // ============================================================
            // 16. Traitement du premier FunctionCall
            // ============================================================

            FunctionCall functionCall = functionCalls.get(0);

            String functionName = functionCall.name()
                    .orElse("");

            Map<String, Object> arguments = functionCall.args()
                    .orElse(Map.of());

            // ============================================================
            // 17. Affichage du FunctionCall
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
            // 18. searchEmployees
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

                // --------------------------------------------------------
                // IMPORTANT :
                // Pas de deuxième appel Gemini.
                // Le backend retourne directement les données à React.
                // --------------------------------------------------------

                return new ChatResponse(
                        "employee_ranking",
                        employees
                );
            }

            // ============================================================
            // 19. getEmployeeProfile
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

                return new ChatResponse(
                        "employee_profile",
                        employee.orElse(Map.of())
                );
            }
// ============================================================
// 20. analyserSituationRH
// ============================================================

            if ("analyserSituationRH".equals(functionName)) {

                String query = String.valueOf(
                        arguments.getOrDefault("query", "")
                );

                System.out.println(
                        "Analyse de carrière pour l'employé : " + query
                );

                Optional<Map<String, Object>> employee =
                        employeService.rechercherProfilEmploye(query);

                if (employee.isEmpty()) {

                    System.out.println(
                            "Aucun employé trouvé pour l'analyse : " + query
                    );

                    return new ChatResponse(
                            "text",
                            "Je n'ai trouvé aucun employé correspondant à « "
                                    + query
                                    + " » dans les données disponibles."
                    );
                }

                Map<String, Object> employeeData = employee.get();

                Integer employeId = convertirEnInteger(
                        employeeData.get("id")
                );

                if (employeId == null) {

                    return new ChatResponse(
                            "text",
                            "L'identifiant de l'employé n'est pas disponible "
                                    + "dans les données retournées par le backend."
                    );
                }

                System.out.println(
                        "Employé identifié : " + employeeData
                );

                System.out.println(
                        "ID employé utilisé pour l'analyse : " + employeId
                );

                Optional<Map<String, Object>> analyse =
                        analyseCarriereService.analyser(employeId);

                if (analyse.isEmpty()) {

                    return new ChatResponse(
                            "text",
                            "Je ne peux pas effectuer l'analyse de carrière "
                                    + "pour cet employé avec les données et règles "
                                    + "actuellement disponibles."
                    );
                }

                System.out.println(
                        "Analyse carrière : " + analyse.get()
                );

                return new ChatResponse(
                        "career_analysis",
                        analyse.get()
                );
            }
            // ============================================================
            // 21. Fonction inconnue
            // ============================================================

            return new ChatResponse(
                    "text",
                    response.text()
            );

        } catch (Exception e) {

            e.printStackTrace();

            return new ChatResponse(
                    "text",
                    "Erreur lors de la communication avec l'API Gemini : "
                            + e.getMessage()
            );
        }
    }
    private Integer convertirEnInteger(Object valeur) {

        if (valeur == null) {
            return null;
        }

        if (valeur instanceof Number) {
            return ((Number) valeur).intValue();
        }

        try {
            return Integer.valueOf(valeur.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
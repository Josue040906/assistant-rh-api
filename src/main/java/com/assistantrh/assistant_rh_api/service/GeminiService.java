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
    private final RechercheApproximativeAgentService rechercheApproximativeAgentService;
    private final PosteService posteService;
    private final ServiceService serviceService;

    private int compteurAppelsGemini = 0;

    public GeminiService(
            @Value("${gemini.api.key:}") String apiKey,
            EmployeService employeService,
            RechercheApproximativeAgentService rechercheApproximativeAgentService,
            PosteService posteService,
            ServiceService serviceService
    ) {

        if (apiKey != null && !apiKey.isBlank()) {
            this.client = Client.builder()
                    .apiKey(apiKey)
                    .build();
        } else {
            this.client = new Client();
        }

        this.employeService = employeService;
        this.rechercheApproximativeAgentService =
                rechercheApproximativeAgentService;
        this.posteService = posteService;
        this.serviceService = serviceService;
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
            // 1. SCHÉMAS
            // ============================================================

            Schema querySchema = Schema.builder()
                    .type("STRING")
                    .description(
                            "Texte utilisé pour effectuer une recherche."
                    )
                    .build();

            Schema parametersSchema = Schema.builder()
                    .type("OBJECT")
                    .properties(Map.of(
                            "query", querySchema
                    ))
                    .required(List.of("query"))
                    .build();

            // ============================================================
            // 2. searchEmployees
            // ============================================================

            FunctionDeclaration searchEmployees =
                    FunctionDeclaration.builder()
                            .name("searchEmployees")
                            .description(
                                    "Recherche des employés dans la base RH. "
                                            + "Utilise cette fonction lorsqu'un utilisateur "
                                            + "cherche des employés par nom, prénom, matricule, "
                                            + "poste, service, direction ou compétence."
                            )
                            .parameters(parametersSchema)
                            .build();

            FunctionDeclaration searchEmployeesFuzzy =
                    FunctionDeclaration.builder()
                            .name("searchEmployeesFuzzy")
                            .description(
                                    "Recherche approximativement des agents par nom, prénom "
                                            + "ou identité lorsque le texte est incomplet ou "
                                            + "contient une faute de frappe. Utilise cet outil "
                                            + "pour retrouver des candidats proches; il classe "
                                            + "les résultats par ressemblance et ne renvoie que "
                                            + "les correspondances au-dessus de son seuil. "
                                            + "Pour un matricule, seule une correspondance exacte "
                                            + "est considérée."
                            )
                            .parameters(parametersSchema)
                            .build();

            // ============================================================
            // 3. getEmployeeProfile
            // ============================================================

            FunctionDeclaration getEmployeeProfile =
                    FunctionDeclaration.builder()
                            .name("getEmployeeProfile")
                            .description(
                                    "Récupère le profil détaillé d'un employé précis. "
                                            + "Utilise cette fonction lorsqu'un utilisateur "
                                            + "demande les informations ou le profil d'un employé."
                            )
                            .parameters(parametersSchema)
                            .build();

            // ============================================================
            // 4. searchServices
            // ============================================================

            FunctionDeclaration searchServices =
                    FunctionDeclaration.builder()
                            .name("searchServices")
                            .description(
                                    "Recherche un ou plusieurs services dans la base RH. "
                                            + "La recherche peut utiliser le code du service, "
                                            + "son nom, sa description ou sa direction."
                            )
                            .parameters(parametersSchema)
                            .build();

            // ============================================================
            // 5. searchPostes
            // ============================================================

            FunctionDeclaration searchPostes =
                    FunctionDeclaration.builder()
                            .name("searchPostes")
                            .description(
                                    "Recherche un ou plusieurs postes dans la base RH. "
                                            + "La recherche peut utiliser l'intitulé, la description, "
                                            + "le service ou la direction."
                            )
                            .parameters(parametersSchema)
                            .build();

            // ============================================================
            // 6. searchPostesByService
            // ============================================================

            FunctionDeclaration searchPostesByService =
                    FunctionDeclaration.builder()
                            .name("searchPostesByService")
                            .description(
                                    "Recherche les postes appartenant à un service précis. "
                                            + "Le paramètre query peut être le code ou le nom "
                                            + "du service."
                            )
                            .parameters(parametersSchema)
                            .build();

            // 8. OUTILS
            // ============================================================

            Tool tool = Tool.builder()
                    .functionDeclarations(List.of(
                            searchEmployees,
                            searchEmployeesFuzzy,
                            getEmployeeProfile,
                            searchServices,
                            searchPostes,
                            searchPostesByService
                    ))
                    .build();

            // ============================================================
            // 9. INSTRUCTIONS GEMINI
            // ============================================================

            String systemInstruction = """
            Tu es bandI'Akam, l'assistant intelligent intégré à une application
            de gestion des ressources humaines du Ministère des Budgets et Finances (MEF).

            IDENTITÉ
            --------
            Ton nom est bandI'Akam.

            Le nom "bandI'Akam" signifie "ami / pote" en malgache.

            Si l'utilisateur demande ton nom, réponds naturellement que tu
            t'appelles bandI'Akam et explique brièvement la signification du nom.

            CONVERSATION
            ------------
            Tu dois donner l'impression d'un véritable assistant conversationnel.

            Réponds naturellement et chaleureusement.

            Lorsque les données du backend sont disponibles, tu peux introduire
            le résultat avec une courte phrase naturelle.

            Par exemple :
            - "Oui, bien sûr ! J'ai trouvé les agents que tu recherches."
            - "Oui, voici les postes rattachés à ce service."
            - "J'ai trouvé le profil demandé. Voici les informations disponibles."

            Évite les réponses froides comme :
            "Voici la liste."

            Mais reste professionnel dans un contexte RH.

            IMPORTANT :
            La formulation naturelle ne doit jamais modifier les données
            retournées par le backend.

            SOURCE DE VÉRITÉ
            ----------------
            PostgreSQL est la source de vérité pour les données RH.

            Tu ne dois jamais inventer :
            - un employé ;
            - un matricule ;
            - un poste ;
            - un service ;
            - une direction ;
            - une date ;
            - une compétence ;
            - une information personnelle.

            COMPRÉHENSION DE LA DEMANDE
            ---------------------------
            Comprendre la demande en langage naturel est ton rôle.

            Tu dois identifier l'intention de l'utilisateur et choisir
            la fonction backend appropriée.

            Ne cherche pas à faire toi-même les recherches dans les données.

            OUTILS DISPONIBLES
            ------------------
            searchEmployees(query)
            Recherche un ou plusieurs employés.

            searchEmployeesFuzzy(query)
            Recherche approximativement des agents par nom ou prénom incomplet
            ou mal orthographié. À utiliser lorsque l'identité fournie est
            approximative; une demande de recherche classique continue
            d'utiliser searchEmployees.

            getEmployeeProfile(query)
            Récupère le profil détaillé d'un employé précis.

            searchServices(query)
            Recherche des services.

            searchPostes(query)
            Recherche des postes.

            searchPostesByService(query)
            Recherche les postes appartenant à un service.

            CHOIX DES OUTILS
            ----------------
            Si l'utilisateur demande des employés, utilise searchEmployees.

            Si l'utilisateur fournit un nom ou un prénom partiel, incertain
            ou visiblement mal orthographié pour retrouver une personne,
            utilise searchEmployeesFuzzy et transmets le texte fourni sans
            le corriger. Ne remplace pas systématiquement searchEmployees
            par cet outil.

            Si l'utilisateur demande le profil détaillé d'un employé précis,
            utilise getEmployeeProfile.

            Si l'utilisateur demande un service ou des informations sur un
            service, utilise searchServices.

            Si l'utilisateur demande un poste ou plusieurs postes,
            utilise searchPostes.

            Si l'utilisateur demande les postes appartenant à un service,
            utilise searchPostesByService.

            FAUTES DE FRAPPE
            ---------------
            Ne corrige pas toi-même les noms avant la recherche.

            Exemple :
            "Heri Rakot"

            doit être transmis au backend comme requête de recherche.

            Le backend est responsable de la recherche réelle.

            MULTIPLES RÉSULTATS
            -------------------
            Si plusieurs résultats sont retournés, présente-les tous
            dans la limite des candidats fournis et demande une précision
            lorsque plusieurs personnes sont plausibles. Un score de
            correspondance est un indicateur technique, pas une probabilité
            ni une preuve d'identité; ne présente pas un candidat comme
            certain en l'absence de données qui le justifient.

            Pour les résultats de searchEmployeesFuzzy, si aucun candidat
            n'est retourné, explique que la recherche n'a pas identifié
            de correspondance suffisamment fiable et invite l'utilisateur
            à essayer une autre orthographe ou à fournir un prénom ou un
            matricule. Ne crée ni ne complète aucune information absente
            des résultats backend.

            Ne choisis jamais arbitrairement un employé ou un poste.

            AUCUN RÉSULTAT
            --------------
            Si aucune donnée n'est retournée, indique simplement que
            rien ne correspond à la recherche dans les données disponibles.

            ANALYSE DE CARRIÈRE
            -------------------
            Ne calcule jamais toi-même l'ancienneté.

            Tu dois simplement présenter et expliquer les résultats retournés.

            SÉPARATION DES RESPONSABILITÉS
            ------------------------------
            Gemini :
            - comprend la demande ;
            - identifie l'intention ;
            - extrait les paramètres ;
            - choisit l'outil ;
            - formule naturellement la réponse.

            Spring Boot :
            - exécute les fonctions ;
            - interroge PostgreSQL ;
            - retourne les données RH disponibles.

            PostgreSQL :
            - fournit les données RH réelles.

            Ne contourne jamais cette séparation.
            """;

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
            // 10. CHAT GEMINI
            // ============================================================

            Chat chat = client.chats.create(
                    "gemini-3.6-flash",
                    config
            );

            // ============================================================
            // 11. PREMIER APPEL
            // ============================================================

            afficherAppelGemini(
                    "Compréhension de la demande / Function Calling"
            );

            GenerateContentResponse response =
                    chat.sendMessage(messageUtilisateur);

            // ============================================================
            // 12. RÉCUPÉRATION DU FUNCTION CALL
            // ============================================================

            List<FunctionCall> functionCalls = response.parts()
                    .stream()
                    .map(Part::functionCall)
                    .flatMap(Optional::stream)
                    .toList();

            // ============================================================
            // 13. PAS DE FUNCTION CALL
            // ============================================================

            if (functionCalls.isEmpty()) {

                return new ChatResponse(
                        "text",
                        response.text()
                );
            }

            // ============================================================
            // 14. FUNCTION CALL
            // ============================================================

            FunctionCall functionCall = functionCalls.get(0);

            String functionName =
                    functionCall.name().orElse("");

            Map<String, Object> arguments =
                    functionCall.args().orElse(Map.of());

            String functionId =
                    functionCall.id().orElse("");

            System.out.println();
            System.out.println("===== FUNCTION CALL =====");
            System.out.println("Nom       : " + functionName);
            System.out.println("Arguments : " + arguments);
            System.out.println("ID        : " + functionId);
            System.out.println("=========================");
            System.out.println();

            // ============================================================
            // 15. EXÉCUTION BACKEND
            // ============================================================

            Object resultatBackend;

            switch (functionName) {

                case "searchEmployees" -> {

                    String query =
                            String.valueOf(
                                    arguments.getOrDefault(
                                            "query",
                                            ""
                                    )
                            );

                    System.out.println(
                            "Recherche employés : " + query
                    );

                    resultatBackend =
                            employeService.rechercherEmployes(query);
                }

                case "searchEmployeesFuzzy" -> {

                    String query =
                            String.valueOf(
                                    arguments.getOrDefault(
                                            "query",
                                            ""
                                    )
                            );

                    System.out.println(
                            "Recherche approximative d'agents : " + query
                    );

                    resultatBackend =
                            rechercheApproximativeAgentService
                                    .rechercherAgentsApproximatifs(query);
                }

                case "getEmployeeProfile" -> {

                    String query =
                            String.valueOf(
                                    arguments.getOrDefault(
                                            "query",
                                            ""
                                    )
                            );

                    System.out.println(
                            "Profil employé : " + query
                    );

                    Optional<Map<String, Object>> employee =
                            employeService.rechercherProfilEmploye(query);

                    resultatBackend =
                            employee.orElse(Map.of());
                }

                case "searchServices" -> {

                    String query =
                            String.valueOf(
                                    arguments.getOrDefault(
                                            "query",
                                            ""
                                    )
                            );

                    System.out.println(
                            "Recherche services : " + query
                    );

                    resultatBackend =
                            serviceService.rechercherServices(query);
                }

                case "searchPostes" -> {

                    String query =
                            String.valueOf(
                                    arguments.getOrDefault(
                                            "query",
                                            ""
                                    )
                            );

                    System.out.println(
                            "Recherche postes : " + query
                    );

                    resultatBackend =
                            posteService.rechercherPostes(query);
                }

                case "searchPostesByService" -> {

                    String query =
                            String.valueOf(
                                    arguments.getOrDefault(
                                            "query",
                                            ""
                                    )
                            );

                    System.out.println(
                            "Recherche postes du service : " + query
                    );

                    List<Map<String, Object>> services =
                            serviceService.rechercherServices(query);

                    if (services.isEmpty()) {

                        resultatBackend = List.of();

                    } else {

                        Map<String, Object> service =
                                services.get(0);

                        Integer serviceId =
                                convertirEnInteger(
                                        service.get("id")
                                );

                        if (serviceId == null) {

                            resultatBackend = List.of();

                        } else {

                            System.out.println(
                                    "Service trouvé : "
                                            + service.get("nom")
                                            + " (ID=" + serviceId + ")"
                            );

                            resultatBackend =
                                    posteService.rechercherPostesParService(
                                            serviceId.longValue()
                                    );
                        }
                    }
                }
                default -> {

                    return new ChatResponse(
                            "text",
                            "Je ne peux pas traiter cette opération."
                    );
                }
            }

            System.out.println(
                    "===== RÉSULTAT BACKEND ====="
            );
            System.out.println(resultatBackend);
            System.out.println(
                    "============================"
            );

            // ============================================================
            // 16. DEUXIÈME APPEL GEMINI :
            // FORMULATION NATURELLE
            // ============================================================
            //
            // Le résultat vient du backend.
            // Gemini ne fait ici que transformer ce résultat en réponse
            // conversationnelle.
            //
            // Cela consomme une deuxième requête Gemini.
            // Nous ne l'utiliserons que pour les demandes passées par
            // Gemini. Les demandes entièrement locales pourront éviter
            // Gemini plus tard.
            // ============================================================

            FunctionResponse functionResponse =
                    FunctionResponse.builder()
                            .id(functionId)
                            .name(functionName)
                            .response(
                                    Map.of(
                                            "output",
                                            resultatBackend
                                    )
                            )
                            .build();

            Content functionResponseContent =
                    Content.fromParts(
                            Part.builder()
                                    .functionResponse(functionResponse)
                                    .build()
                    );

            afficherAppelGemini(
                    "Formulation naturelle de la réponse"
            );

            GenerateContentResponse finalResponse =
                    chat.sendMessage(functionResponseContent);

            String texteFinal = finalResponse.text();

            if (texteFinal == null || texteFinal.isBlank()) {

                return new ChatResponse(
                        "text",
                        "J'ai trouvé les informations demandées."
                );
            }

            // ============================================================
            // 17. RÉPONSE AU FRONTEND
            // ============================================================

            return new ChatResponse(
                    determinerTypeReponse(functionName),
                    Map.of(
                            "message",
                            texteFinal,
                            "data",
                            resultatBackend
                    )
            );

        } catch (Exception e) {

            e.printStackTrace();

            return new ChatResponse(
                    "text",
                    "Une erreur est survenue lors du traitement "
                            + "de votre demande : "
                            + e.getMessage()
            );
        }
    }

    private String determinerTypeReponse(String functionName) {

        return switch (functionName) {

            case "searchEmployees",
                 "searchEmployeesFuzzy" ->
                    "employee_list";

            case "getEmployeeProfile" ->
                    "employee_profile";

            case "searchServices" ->
                    "service_list";

            case "searchPostes",
                 "searchPostesByService" ->
                    "poste_list";

            default ->
                    "text";
        };
    }

    private Integer convertirEnInteger(Object valeur) {

        if (valeur == null) {
            return null;
        }

        if (valeur instanceof Number) {
            return ((Number) valeur).intValue();
        }

        try {
            return Integer.valueOf(
                    valeur.toString()
            );

        } catch (NumberFormatException e) {

            return null;
        }
    }
}
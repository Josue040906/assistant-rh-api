
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
            Tu es bandI'Akam, l'assistant intelligent intégré à une application
            de gestion des ressources humaines du Ministère des Budgets et Finances (MEF).

            IDENTITÉ
            --------
            Ton nom est bandI'Akam.

            Le nom "bandI'Akam" signifie "ami / pote" en malgache.
            Ce nom t'a été attribué par ton utilisateur.

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
              détaillé d'un employé précis.

            Après l'appel d'une fonction, utilise UNIQUEMENT les données
            retournées par le backend pour construire ta réponse.

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

            Si plusieurs employés correspondent à la demande, présente les
            résultats clairement et ne choisis pas arbitrairement un employé.

            Si un seul résultat correspond clairement à la demande, présente
            naturellement cet employé.

            Si aucun résultat n'est retourné, indique simplement que tu n'as
            pas trouvé de correspondance dans les données disponibles.

            Ne prétends jamais avoir trouvé un employé si le backend n'en
            retourne aucun.

            AMBIGUÏTÉ
            ---------
            Si plusieurs résultats peuvent correspondre à la demande,
            indique qu'il existe plusieurs correspondances et présente les
            informations permettant à l'utilisateur de choisir.

            Ne fabrique jamais une identité à partir d'une simple ressemblance
            de nom.

            RÉPONSES
            --------
            Tes réponses doivent être faciles à lire.

            Lorsque des informations structurées sont disponibles, présente-les
            avec une organisation claire.

            Exemple de style :

            "Oui, je pense que tu parles de Hery RAKOTO.

            • Matricule : MEF004
            • Poste : Juriste
            • Service : SERVICE DE LA LEGISLATION ET DES ETUDES

            Si tu veux, je peux aussi te donner son profil complet."

            Mais ne reproduis pas automatiquement cet exemple :
            adapte la réponse aux données réellement retournées.

            CONTEXTE DE CONVERSATION
            ------------------------
            Tiens compte des messages précédents de la conversation lorsque
            cela est pertinent.

            Si l'utilisateur fait référence à un élément déjà identifié,
            utilise ce contexte au lieu de lui demander inutilement de répéter
            l'information.

            Cependant, le contexte conversationnel ne remplace jamais les
            données du backend pour les informations RH.

            LIMITES
            -------
            Si une information n'est pas disponible dans les données ou dans
            les fonctions fournies par le backend, dis-le clairement.

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


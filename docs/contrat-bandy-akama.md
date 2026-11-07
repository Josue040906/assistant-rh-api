# Contrat d'integration Bandy Akama / assistant-rh-api

## Catalogue et authentification

L'API n'expose ni catalogue de tools, ni endpoint de decouverte, ni identite technique Bandy. `search_agents` est l'identifiant du tool a enregistrer dans Bandy. Pour la recherche multicritere avancee, l'URL exposee est `POST /api/recherche/agents/avancee`; la route historique `/api/recherche/agents/criteres` reste disponible pour les integrations existantes.

Bandy transmet sans le modifier le jeton obtenu par `POST /api/utilisateurs/login`:

```http
Authorization: Bearer <access_token>
Content-Type: application/json
```

Le backend verifie la signature HMAC-SHA256, l'expiration du jeton et le compte actif de l'utilisateur. Les recherches sont reservees aux roles `SPERS_AGENT` et `SPERS_CHEF` rattaches au service SYGPERS (id 2), selon l'affectation en cours. L'identite vient du sujet du jeton, jamais d'un `userId` fourni par Bandy. Ne pas stocker ni journaliser le jeton.

Les routes de recherche historiques restent disponibles:

| Methode et route | Contrat |
|---|---|
| `GET /api/recherche/agents?query=...` | Recherche textuelle; liste de maps SQL. |
| `GET /api/recherche/agents/approximative?query=...` | Recherche approximative nom/matricule; `{ "query": "...", "total": 0, "agents": [] }`, au plus 10 resultats. |
| `POST /api/recherche/agents/criteres` | Recherche multicritere historique au format `filters`. |
| `POST /api/recherche/agents/avancee` | Recherche multicritere avancee au format AST booleen, utilisee par `search_agents`. |
| `GET /api/profil/agent?query=...` | Profil d'un agent identifie par nom ou matricule; endpoint utilise par `get_agent_profile`. |

Les trois routes exigent un jeton et les autorisations ci-dessus. Le token expire apres une heure; un token mal forme, expire ou signe incorrectement retourne `401`, et un compte non autorise retourne `403`.

## Recherche multicritere

Le DTO HTTP attend un objet avec la propriete `criteriaJson` de type chaine. Le JSON des filtres ne doit pas etre envoye directement a la racine:

```json
{
  "criteriaJson": "{\"filters\":[{\"field\":\"sexe\",\"operator\":\"EQ\",\"value\":\"M\"},{\"field\":\"age\",\"operator\":\"LT\",\"value\":30},{\"field\":\"service\",\"operator\":\"CONTAINS\",\"value\":\"finances\"}],\"limit\":20,\"sortBy\":\"nom\",\"sortDirection\":\"ASC\"}"
}
```

Le tool Bandy doit transmettre ce corps et le bearer de l'utilisateur. Les criteres sont valides par l'API puis combines par `AND` dans une requete parametree. Les noms de champ et les operateurs sont autorises par liste blanche; aucun SQL envoye par le modele n'est interprete.

### Champs et operateurs actuellement implementes

| Type | Champs | Operateurs |
|---|---|---|
| Texte | `matricule`, `nom`, `prenom`, `sexe`, `adresse`, `poste`, `service`, `direction`, `categorie`, `corps`, `type_emploi`, `classe` | `EQ`, `NE`, `CONTAINS`, `FUZZY` |
| Entier | `age`, `echelon` | `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `BETWEEN` |
| Date ISO `AAAA-MM-JJ` | `date_naissance`, `date_embauche` | `EQ`, `NE`, `BEFORE`, `AFTER`, `BETWEEN`, `WITHIN_LAST_YEARS`, `OLDER_THAN_YEARS` |

Le service valide de 1 a 10 filtres, une taille de document JSON maximale de 8 000 caracteres, les valeurs textuelles de 1 a 150 caracteres, les dates calendaires, les entiers et les bornes. L'age est calcule en annees revolues et accepte des valeurs de 0 a 120. `FUZZY` exige 4 caracteres minimum et est interdit pour le matricule et le sexe. Les comparaisons textuelles sont normalisees en casse et accents; `CONTAINS` traite `%`, `_` et `\` comme du texte litteral. Les sexes feminin et masculin sont normalises respectivement en `F` et `M`.

`limit` est compris entre 1 et 50 (defaut 20); `offset` entre 0 et 10 000 (defaut 0). L'API lit au plus `limit + 1` lignes pour calculer `hasMore`. Pour la page suivante, ajouter `limit` a `offset` tant que `hasMore` vaut `true`. `total` est le nombre d'agents renvoyes dans la page pour `LIST`, pas le nombre global de correspondances. `COUNT` execute une requete SQL `COUNT(DISTINCT e.id)` sans charger les lignes agents et renvoie le total global dans `total`, avec `agents: []`. Le tri accepte `matricule`, `nom`, `prenom`, `age`, `date_embauche`, `poste`, `service`, `direction`, `classe` et `echelon`; le sens est `ASC` ou `DESC`. L'id interne `id` est maintenant expose dans chaque agent du DTO pour permettre a Chatfronty d'ouvrir la fiche correspondante. Il sert uniquement a la navigation; l'acces a la fiche reste soumis a l'authentification et aux autorisations API.

Les filtres `grade` et `diplome` ne sont pas implementes par la recherche multicritere actuelle. Ne pas les envoyer; ils sont rejetes comme champs inconnus. Le modele de resultat ne retourne pas non plus l'adresse, le diplome, la date de naissance complete, le CIN, le telephone ou la photo. Il retourne uniquement matricule, nom, prenom, sexe, age calcule, date d'embauche et les informations professionnelles visibles dans le DTO. Les champs absents peuvent etre `null`; `score_correspondance` ne s'interprete que pour `FUZZY`.

La reponse est de la forme:

```json
{
  "total": 1,
  "limite": 20,
  "offset": 0,
  "hasMore": false,
  "agents": [
    {
      "id": 123,
      "matricule": "123",
      "nom": "RAKOTO",
      "prenom": "Faly",
      "sexe": "M",
      "age": 29,
      "date_embauche": "2020-01-01",
      "poste": "Gestionnaire",
      "code_service": "SG",
      "service": "Finances",
      "direction": "Administration",
      "categorie": "A",
      "corps": "Administrateur",
      "typeEmploi": "Fonctionnaire",
      "classe": "I",
      "echelon": 2,
      "score_correspondance": 0.9
    }
  ]
}
```

Le champ `typeEmploi` conserve la casse camelCase du DTO Java. Bandy doit mapper ce nom exact; le filtre d'entree reste `type_emploi`.

### Recherche multicritere avancee

Le tool `search_agents` doit appeler `POST /api/recherche/agents/avancee` avec le meme enveloppement HTTP (`criteriaJson` est une chaine JSON, `resultMode` vaut `LIST` ou `COUNT`) et le bearer de l'utilisateur. Exemple:

```json
{
  "criteriaJson": "{\"must\":[{\"field\":\"direction\",\"operator\":\"CONTAINS\",\"value\":\"Finances\"},{\"field\":\"age\",\"operator\":\"GT\",\"value\":40}],\"must_not\":[{\"field\":\"service\",\"operator\":\"EQ\",\"value\":\"Comptabilite\"}],\"should\":[{\"field\":\"personne\",\"operator\":\"FUZZY_PERSON\",\"value\":\"Rakoto Faly\"}],\"minimum_should_match\":1,\"limit\":20,\"offset\":0}"
}
```

`must` combine les clauses par ET; `should` exprime des alternatives par OU lorsque `minimum_should_match=1`; `must_not` exclut les correspondances. Les groupes peuvent etre imbriques avec une enveloppe `bool`. Au moins un critere positif dans `must` ou dans `should` est obligatoire. Le moteur accepte au plus 40 criteres et une profondeur booleenne maximale de 6.

Champs: `matricule`, `nom`, `prenom`, `personne`, `nom_complet`, `sexe`, `adresse`, `poste`, `service`, `direction`, `categorie`, `corps`, `type_emploi`, `classe`, `age`, `echelon`, `date_naissance`, `date_embauche`. Operateurs textuels: `EQ`, `NE`, `CONTAINS`, `FUZZY`; `FUZZY_PERSON` est reserve a `personne` et `nom_complet`. Operateurs numeriques: `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `BETWEEN`. Operateurs de date: `EQ`, `BEFORE`, `AFTER`, `BETWEEN`, `WITHIN_LAST_MONTHS`, `WITHIN_LAST_YEARS`, `OLDER_THAN_YEARS`. Pour `BETWEEN`, la borne haute est transmise dans `upperValue`.

La taille maximale de `criteriaJson` est de 12 000 caracteres. `limit` est compris entre 1 et 50 (defaut 20), `offset` entre 0 et 10 000 (defaut 0). Le tri accepte `score` (defaut), `nom`, `prenom`, `age`, `date_embauche`, `poste`, `service` et `direction`, avec `sortDirection` egal a `ASC` ou `DESC`. `LIST` retourne `total`, `limit`, `agents`, `offset` et `hasMore`; `COUNT` retourne le total global sans charger les agents. La projection expose uniquement `id`, `nom`, `prenom`, `poste`, `service`, `direction` et `score_correspondance`.

Pour chaque relance d'une recherche en cours, Bandy doit fournir dans `criteriaJson` l'AST complet representant tous les criteres encore actifs. Le nouveau AST remplace le precedent: ne pas envoyer seulement le critere ajoute, ne pas fusionner manuellement les groupes et ne pas reutiliser des clauses exclues. Les criteres doivent etre explicitement retires de l'AST complet lorsqu'ils ne sont plus actifs.

## Erreurs

| HTTP | Reponse |
|---|---|
| `200` | Une page, eventuellement vide: `total: 0`, `agents: []`, `hasMore: false`. |
| `400` | `{"code":"INVALID_REQUEST","message":"..."}` pour un corps HTTP illisible; `{"message":"..."}` pour des criteres internes invalides ou inconnus. |
| `401` | `{"code":"UNAUTHORIZED","message":"..."}` pour un bearer absent ou invalide. |
| `403` | `{"code":"FORBIDDEN","message":"..."}` pour un compte sans autorisation. |
| `503` | `{"code":"RH_DATA_UNAVAILABLE","message":"..."}` lorsque la base RH est indisponible ou en timeout. |

### Mesure et exploitation

Les logs de performance structurent la duree totale HTTP (`api_request`), la requete SQL (`db_query`) et le traitement Java de validation et de mapping (`application_processing`) sous un identifiant de requete. Aucun parametre RH ni bearer n'est journalise par ces mesures.

La recherche structuree selectionne des colonnes DTO et borne le nombre de lignes, sans charger les employes en memoire pour les filtrer. La jointure d'affectation courante s'appuie sur la contrainte unique partielle `uq_affectation_employe_active` creee par la migration `V20261016_01`; les carrieres gardent leur selection de la situation courante la plus recente pour ne pas supposer une contrainte d'unicite non declaree dans les migrations. Les migrations SQL sont conservees dans le depot, mais aucun outil Flyway/Liquibase n'est declare dans `pom.xml`: elles ne sont donc pas executees automatiquement par Spring Boot. Appliquer les migrations par le processus de deploiement de la base. La migration `V20261101_01__enable_fuzzystrmatch.sql` active l'extension PostgreSQL `fuzzystrmatch`, requise par `levenshtein()` dans la recherche approximative; elle doit etre appliquee avec un role autorise a creer les extensions avant d'utiliser cette recherche. Ne pas ajouter d'index avant analyse des plans et des volumes de production.

Sur la base locale de 29 employes, une comparaison `EXPLAIN ANALYZE` de six repetitions de la meme requete (age < 120, page de 21 lignes, meme selection et ordre) donne une mediane SQL de 1,96 ms avant et 1,07 ms apres le remplacement des recherches lateralisees par les jointures et la selection `DISTINCT ON` (environ 45 % de reduction). Une comparaison `EXCEPT ALL` sur toutes les colonnes du DTO retourne zero difference. Cette base n'est pas representative d'une charge de production.

Un appel HTTP authentifie au meme endpoint a ensuite ete mesure sur huit requetes apres chauffe: mediane cliente PowerShell de 19,93 ms; les telemetries serveur donnent environ 13,93 ms par requete HTTP, 7,42 ms SQL, 0,22 ms pour valider/parser et 0,38 ms pour mapper la page. Ces chiffres locaux ne sont pas une comparaison HTTP avant/apres et incluent les couts de la machine et de la connexion. Aucun nouvel index ni cache RH n'est ajoute sur la base de ces seuls volumes. Hikari est auto-configure par Spring Boot sans parametrage de pool explicite; aucun timeout de requete SQL applicatif n'est configure. Mesurer le pool et definir des limites de requete/connectivite dans l'environnement de deploiement avant de les ajuster.

## Fichiers sensibles et stockage

Les sauvegardes SQL, exports RH, artefacts opaques et photos d'employes ne doivent pas etre versionnes. Les exports et photos suivis par Git ont ete retires de l'index courant et sont ignores; les fichiers locaux n'ont pas ete effaces. Les sauvegardes locales deja ignorees restent en place. En production, fournir les photos depuis un volume prive persistant ou un stockage objet autorise, et conserver les sauvegardes dans un espace chiffre a acces restreint. Le gestionnaire local continue de lire `uploads/employes/`.

Une suppression du dernier commit ne purge pas l'historique Git deja publie. Les responsables du depot doivent retirer ces blobs des refs publiees et renouveler tout secret qui aurait ete present dans les fichiers exposes avant de republier l'historique.

## Consultation de l'organisation par Bandy Akama

La consultation de l'organigramme est exposée par une route en lecture seule :

`GET /api/organisation`

Cette route exige le même bearer JWT que les recherches d'agents. Le backend valide le jeton et vérifie que son titulaire est un agent SPERS actif. Bandy transmet le jeton de l'utilisateur connecté; il ne transmet jamais un identifiant utilisateur libre.

Paramètres de requête :

| Paramètre | Obligatoire | Rôle |
|---|---:|---|
| `operation` | Oui | Opération autorisée ci-dessous |
| `id` | Selon opération | Identifiant d'une direction, d'un service ou d'un poste |
| `directionId` | Non | Filtre sur une direction |
| `serviceId` | Non | Filtre sur un service |
| `query` | Non | Recherche partielle sur les noms, codes ou descriptions prévus |

Opérations disponibles :

| Opération | Paramètres utiles | Résultat |
|---|---|---|
| `LIST_DIRECTIONS` | `query` | Directions avec nombres de services et de postes |
| `GET_DIRECTION` | `id` | Une direction et ses compteurs |
| `LIST_SERVICES` | `directionId`, `query` | Services, direction parente et nombre de postes |
| `GET_SERVICE` | `id` | Un service et son nombre de postes |
| `LIST_POSTES` | `serviceId`, `query` | Postes, structure parente et nombre de titulaires actuels |
| `GET_POSTE` | `id` | Un poste et son nombre de titulaires actuels |
| `GET_HIERARCHY` | `directionId` | Lignes de l'organigramme direction → services → postes |
| `GET_RESPONSIBLE` | `directionId` ou `serviceId` | Tous les titulaires des postes dont l'intitulé contient chef, directeur/directrice ou responsable dans le périmètre |
| `LIST_RESPONSIBLES` | `directionId`, `serviceId` | Liste des titulaires des postes identifiés comme fonctions de responsabilité |
| `COUNT_DIRECTIONS` | Aucun | Nombre de directions |
| `COUNT_SERVICES` | `directionId` facultatif | Nombre de services, éventuellement dans une direction |
| `COUNT_POSTES` | `serviceId` facultatif | Nombre de postes, éventuellement dans un service |
| `COUNT_EMPLOYEES_BY_STRUCTURE` | `directionId`, `serviceId` | Nombre d'agents distincts affectés actuellement au périmètre |

Exemples :

```http
GET /api/organisation?operation=LIST_DIRECTIONS
Authorization: Bearer <access_token>
```

```http
GET /api/organisation?operation=LIST_SERVICES&directionId=3
Authorization: Bearer <access_token>
```

```http
GET /api/organisation?operation=GET_RESPONSIBLE&serviceId=8
Authorization: Bearer <access_token>
```

Une affectation est considérée active à la date du jour si `date_debut <= CURRENT_DATE` et si `date_fin` est nulle ou supérieure ou égale à la date du jour. Les consultations de postes et de responsables appliquent cette règle; elles ne réutilisent pas le filtre spécial de `PosteRepository.findByService`, qui sert à l'écran d'affectation.

Les résultats de responsables peuvent contenir plusieurs titulaires pour un même poste et un même service. L'API retourne tous les titulaires trouvés, sans en choisir un arbitrairement. Le poste est un indice de fonction, pas une preuve d'une relation hiérarchique individuelle.

`GET_EMPLOYEE_SUPERVISION` n'est volontairement pas implémentée : le modèle vérifié ne contient pas de relation explicite agent → supérieur hiérarchique. L'appel retourne `501 NOT_IMPLEMENTED` avec cette explication. L'organigramme direction → service → poste, lui, est consultable.

Erreurs : `400` pour une opération inconnue ou un paramètre obligatoire absent; `401` pour un jeton absent/invalide; `403` pour un compte qui n'est pas un agent SPERS actif; `404` pour un identifiant inexistant; `501` pour la relation de supervision individuelle non disponible.

La table `direction` n'a pas de colonne de sigle : une recherche par sigle (DGB, DSI…) ne trouve rien. L'assistant liste alors les directions (`LIST_DIRECTIONS` sans `query`) et rapproche lui-même le sigle du libellé.

## Refus lisibles pour Bandy Akama

Bandy transmet les refus (4xx) à Gemini, qui les explique à l'utilisateur : chaque refus porte un `message` lisible.

| Route | Cas | Réponse |
|---|---|---|
| `POST /api/recherche/agents/avancee` | aucun critère positif (`must` vide et `should` vide ou `minimum_should_match` = 0, ou seulement `must_not`) | `400`, `message` : « Au moins un critère positif dans must ou should est obligatoire. » |
| `GET /api/profil/agent?query=` | aucune identité exacte | `404`, `code` `AGENT_INTROUVABLE` et `message` |
| `GET /api/profil/agent?query=` | plusieurs agents portent exactement cette identité | `409`, `code` `AGENT_AMBIGU`, `message` et `candidats` (matricule, nom, prénom, poste, service) |

`/api/profil/agent` ne rapproche plus une identité partielle : `query` doit être le matricule ou le nom complet (prénom nom ou nom prénom, casse et espaces ignorés). L'identification approximative passe par la recherche d'agents ; l'API ne choisit jamais un homonyme à la place de l'utilisateur.
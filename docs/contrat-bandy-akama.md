# Contrat d'integration Bandy Akama / assistant-rh-api

## Catalogue et authentification

L'API n'expose ni catalogue de tools, ni endpoint de decouverte, ni identite technique Bandy. `rh.agents.search.criteria` est un identifiant de tool a enregistrer dans Bandy, pas un nom declare par le backend. L'URL effectivement exposee est `POST /api/recherche/agents/criteres`.

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
| `POST /api/recherche/agents/criteres` | Recherche multicritere decrite ci-dessous. |

Les trois routes exigent un jeton et les autorisations ci-dessus. Le token expire apres une heure; un token mal forme, expire ou signe incorrectement retourne `401`, et un compte non autorise retourne `403`.

## Profil global d'un agent pour Bandy

`GET /api/profil/agent/assistant?nom_agent=...` retourne un profil concis pour
le tool unique `get_agent_profile`. `nom_agent` doit être le nom complet
(prénom puis nom, ou nom puis prénom) ou le matricule exact. Une recherche
partielle ne renvoie pas un autre agent par défaut. Cette route requiert le
même bearer et le même compte SPERS actif que les autres consultations
protégées.

La réponse contient uniquement le nom, le matricule, l'âge calculé, le sexe,
la date d'embauche, le type d'emploi, la catégorie, le corps, le poste actuel,
le service, la direction, les diplômes et le parcours de carrière. Elle
n'expose ni CIN, ni téléphone, ni adresse, ni date de naissance complète.
L'API RH ne gère actuellement aucune donnée de compétences : le profil retourne
`competencesDisponibles: false` et une liste vide, sans inférence du modèle.

Exemple de réponse :

```json
{
  "nom": "RAKOTO",
  "prenom": "Mialy",
  "matricule": "AG-19",
  "age": 36,
  "sexe": "F",
  "dateEmbauche": "2018-04-02",
  "typeEmploi": "Fonctionnaire",
  "categorie": "A",
  "corps": "Administrateur",
  "poste": "Gestionnaire",
  "service": "Service RH",
  "direction": "Administration",
  "diplomes": [],
  "parcoursCarriere": [],
  "competencesDisponibles": false,
  "competences": []
}
```

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

`limit` est compris entre 1 et 50 (defaut 20); `offset` entre 0 et 10 000 (defaut 0). L'API lit au plus `limit + 1` lignes pour calculer `hasMore`. Pour la page suivante, ajouter `limit` a `offset` tant que `hasMore` vaut `true`. `total` est le nombre d'agents renvoyes dans la page, pas le nombre global de correspondances. Le tri accepte `matricule`, `nom`, `prenom`, `age`, `date_embauche`, `poste`, `service`, `direction`, `classe` et `echelon`; le sens est `ASC` ou `DESC`. L'id interne departage les ex aequo et n'est pas expose.

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

### Erreurs

| HTTP | Reponse |
|---|---|
| `200` | Une page, eventuellement vide: `total: 0`, `agents: []`, `hasMore: false`. |
| `400` | `{"code":"INVALID_REQUEST","message":"..."}` pour un corps HTTP illisible; `{"message":"..."}` pour des criteres internes invalides ou inconnus. |
| `401` | `{"code":"UNAUTHORIZED","message":"..."}` pour un bearer absent ou invalide. |
| `403` | `{"code":"FORBIDDEN","message":"..."}` pour un compte sans autorisation. |
| `503` | `{"code":"RH_DATA_UNAVAILABLE","message":"..."}` lorsque la base RH est indisponible ou en timeout. |

### Mesure et exploitation

Les logs de performance structurent la duree totale HTTP (`api_request`), la requete SQL (`db_query`) et le traitement Java de validation et de mapping (`application_processing`) sous un identifiant de requete. Aucun parametre RH ni bearer n'est journalise par ces mesures.

La recherche structuree selectionne des colonnes DTO et borne le nombre de lignes, sans charger les employes en memoire pour les filtrer. La jointure d'affectation courante s'appuie sur la contrainte unique partielle `uq_affectation_employe_active` creee par la migration `V20261016_01`; les carrieres gardent leur selection de la situation courante la plus recente pour ne pas supposer une contrainte d'unicite non declaree dans les migrations. Une migration SQL existe dans le depot, mais aucun outil Flyway/Liquibase n'est declare dans `pom.xml`: appliquer les migrations par le processus de deploiement de la base. Ne pas ajouter d'index avant analyse des plans et des volumes de production.

Sur la base locale de 29 employes, une comparaison `EXPLAIN ANALYZE` de six repetitions de la meme requete (age < 120, page de 21 lignes, meme selection et ordre) donne une mediane SQL de 1,96 ms avant et 1,07 ms apres le remplacement des recherches lateralisees par les jointures et la selection `DISTINCT ON` (environ 45 % de reduction). Une comparaison `EXCEPT ALL` sur toutes les colonnes du DTO retourne zero difference. Cette base n'est pas representative d'une charge de production.

Un appel HTTP authentifie au meme endpoint a ensuite ete mesure sur huit requetes apres chauffe: mediane cliente PowerShell de 19,93 ms; les telemetries serveur donnent environ 13,93 ms par requete HTTP, 7,42 ms SQL, 0,22 ms pour valider/parser et 0,38 ms pour mapper la page. Ces chiffres locaux ne sont pas une comparaison HTTP avant/apres et incluent les couts de la machine et de la connexion. Aucun nouvel index ni cache RH n'est ajoute sur la base de ces seuls volumes. Hikari est auto-configure par Spring Boot sans parametrage de pool explicite; aucun timeout de requete SQL applicatif n'est configure. Mesurer le pool et definir des limites de requete/connectivite dans l'environnement de deploiement avant de les ajuster.

## Fichiers sensibles et stockage

Les sauvegardes SQL, exports RH, artefacts opaques et photos d'employes ne doivent pas etre versionnes. Les exports et photos suivis par Git ont ete retires de l'index courant et sont ignores; les fichiers locaux n'ont pas ete effaces. Les sauvegardes locales deja ignorees restent en place. En production, fournir les photos depuis un volume prive persistant ou un stockage objet autorise, et conserver les sauvegardes dans un espace chiffre a acces restreint. Le gestionnaire local continue de lire `uploads/employes/`.

Une suppression du dernier commit ne purge pas l'historique Git deja publie. Les responsables du depot doivent retirer ces blobs des refs publiees et renouveler tout secret qui aurait ete present dans les fichiers exposes avant de republier l'historique.

# Contrat d'integration Bandy Akama / assistant-rh-api

## Architecture et authentification

`assistant-rh-api` est une API Spring Boot 4.1.1 / Java 17 utilisant PostgreSQL et `JdbcTemplate`. Les regles RH, la validation, l'acces aux donnees et les traces d'activite restent dans cette API. Elle ne gere ni conversations, ni memoire IA.

Il n'existe actuellement ni registre de tools, ni endpoint de decouverte, ni identite technique Bandy Akama. Les identifiants de tool ci-dessous sont des noms stables a mapper dans Bandy Akama, pas des identifiants exposes par un endpoint de decouverte.

Pour chaque appel, Bandy Akama doit transmettre sans le modifier le bearer token obtenu par l'utilisateur via `POST /api/utilisateurs/login`:

```http
Authorization: Bearer <accessToken>
```

Le backend verifie la signature HMAC-SHA256 et l'expiration du JWT, puis prend le sujet du jeton comme identifiant utilisateur. Ne pas envoyer de `userId`, role ou identifiant d'agent pour remplacer cette identite. Les trois routes de recherche refusent les comptes qui ne sont pas actifs, de role `SPERS_AGENT` ou `SPERS_CHEF`, et rattaches au service SYGPERS (service id 2), controles au moment de chaque appel. Bandy Akama ne doit pas conserver ni journaliser le bearer token.

Les routes suivantes demeurent disponibles pour les usages historiques de l'application, mais ne sont pas le contrat recommande pour le tool de recherche structuree:

| Route | Fonction | Authentification |
|---|---|---|
| `GET /api/recherche/agents?query=...` | Recherche textuelle historique | Bearer JWT + compte SPERS actif |
| `GET /api/recherche/agents/approximative?query=...` | Correspondance approximative par nom/matricule, au plus 10 resultats | Bearer JWT + compte SPERS actif |

Ces deux routes ont des schemas de reponse historiques a base de maps SQL. Pour ne pas coupler Bandy Akama a ces schemas, utiliser plutot le tool `rh.agents.search.criteria` ci-dessous.

## Tool recommande: `rh.agents.search.criteria`

**Description:** rechercher des agents en combinant des criteres RH structures, sans exiger de nom ou de matricule. Recherche en lecture seule.

**Execution:** `POST /api/recherche/agents/criteres`, `Content-Type: application/json`.

Le contrat HTTP actuel encapsule le document de criteres sous forme de chaine JSON dans `criteriaJson`. Le document interne ne doit pas etre envoye comme champs JSON au premier niveau.

```json
{
  "criteriaJson": "{\"filters\":[{\"field\":\"sexe\",\"operator\":\"EQ\",\"value\":\"M\"},{\"field\":\"age\",\"operator\":\"LT\",\"value\":30},{\"field\":\"service\",\"operator\":\"CONTAINS\",\"value\":\"finances\"}],\"limit\":20,\"sortBy\":\"nom\",\"sortDirection\":\"ASC\"}"
}
```

Le resultat est un objet:

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
      "type_emploi": "Fonctionnaire",
      "classe": "I",
      "echelon": 2,
      "score_correspondance": 0.9
    }
  ]
}
```

Les valeurs personnelles sont limitees aux champs selectionnes pour la recherche et sa restitution: matricule, nom, prenom, sexe, age calcule et date d'embauche. L'adresse, la date de naissance complete, le CIN, le telephone, la photo et les identifiants internes ne sont pas retournes. Les informations professionnelles retournees sont poste, service/code, direction, categorie, corps, type d'emploi, classe et echelon. Les champs absents en base peuvent etre `null`. `score_correspondance` n'est significatif que pour un filtre `FUZZY`. `total` est le nombre de lignes de cette page (au plus `limite`), pas un decompte global de tous les resultats. `hasMore` indique si au moins une ligne supplementaire existe apres la page courante.

### Criteres et validation

Le document interne accepte uniquement `filters` (obligatoire), `limit`, `offset`, `sortBy` et `sortDirection`. Il doit contenir de 1 a 10 filtres. Chaque filtre accepte uniquement `field`, `operator`, `value` et, pour `BETWEEN`, `upperValue`. Les filtres sont combines par `AND`.

| Type | Champs | Operateurs |
|---|---|---|
| Texte | `matricule`, `nom`, `prenom`, `sexe`, `adresse`, `poste`, `service`, `direction`, `categorie`, `corps`, `type_emploi`, `classe` | `EQ`, `NE`, `CONTAINS`, `FUZZY` |
| Entier | `age`, `echelon` | `EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `BETWEEN` |
| Date ISO `AAAA-MM-JJ` | `date_naissance`, `date_embauche` | `EQ`, `NE`, `BEFORE`, `AFTER`, `BETWEEN`, `WITHIN_LAST_YEARS`, `OLDER_THAN_YEARS` |

- Les champs et operateurs sont sensibles a leur nom, mais `field` est normalise en minuscules et `operator` en majuscules.
- Valeur texte: 1 a 150 caracteres. Recherche textuelle normalisee sans distinction de casse/accents. `CONTAINS` recherche un fragment litteral; `%`, `_` et `\\` sont echappes.
- `FUZZY` exige au moins 4 caracteres et est interdit pour `matricule`, `sexe` et `adresse`. Le seuil de similarite PostgreSQL est 0.42.
- `sexe` normalise les alias feminin (`F`, `FEMME`, `FEMMES`, `FEMININ`, `FÉMININ`) en `F` et masculin (`M`, `HOMME`, `HOMMES`, `MASCULIN`) en `M`.
- `age` est le nombre d'annees revolues calcule depuis `date_naissance`; valeur entiere de 0 a 120. `GT`/`LT` sont stricts, `GTE`/`LTE` inclusifs et `BETWEEN` inclut ses deux bornes.
- Les entiers sont non negatifs; les deux bornes `BETWEEN` doivent etre ordonnees.
- Les dates sont validees calendriquement. `BEFORE` et `AFTER` sont stricts; `BETWEEN` inclut les deux dates. Les periodes relatives acceptent une valeur entiere de 1 a 100 annees; `WITHIN_LAST_YEARS` inclut la date seuil (`>=`) et `OLDER_THAN_YEARS` l'inclut (`<=`).
- `limit` est compris entre 1 et 50, defaut 20. `offset` (nombre de lignes a ignorer) est compris entre 0 et 10 000, defaut 0. Pour lire la page suivante, avancer `offset` de `limit` tant que `hasMore` vaut `true`. L'API lit au plus `limit + 1` lignes pour calculer ce drapeau; le document interne ne depasse pas 8 000 caracteres.
- `sortBy` facultatif: `matricule`, `nom`, `prenom`, `age`, `date_embauche`, `poste`, `service`, `direction`, `classe`, `echelon`. Defaut `nom`. `sortDirection`: `ASC` ou `DESC`, defaut `ASC`. L'identifiant interne de l'agent sert de dernier critere pour stabiliser l'ordre.
- Le champ `statut` n'est pas pris en charge par ce contrat, meme si des donnees de statut existent dans le modele RH: la recherche structuree ne les joint pas actuellement.

Les valeurs sont liees comme parametres SQL; noms de champs, operateurs et expressions de tri sont choisis dans des listes blanches. Une recherche avec des dates, age ou filtres texte nuls en base ne matche pas ces lignes pour ces filtres.

### Erreurs

| HTTP | Code/corps | Signification |
|---|---|---|
| `200` | `{"total":0,"limite":20,"offset":0,"hasMore":false,"agents":[]}` | Aucun resultat; ce n'est pas une erreur |
| `400` | `{"code":"INVALID_REQUEST","message":"..."}` pour un corps HTTP JSON illisible; `{"message":"..."}` pour des criteres invalides | JSON interne invalide, champ/operateur inconnu, valeur invalide ou combinaison incoherente |
| `401` | `{"code":"UNAUTHORIZED","message":"..."}` | Bearer absent, mal forme, expire ou signature invalide |
| `403` | `{"code":"FORBIDDEN","message":"..."}` | Compte non actif/non autorise ou non rattache a SYGPERS |
| `503` | `{"code":"RH_DATA_UNAVAILABLE","message":"..."}` | Dependance de donnees indisponible; aucun detail SQL n'est retourne |
| `500` | Erreur serveur generique Spring | Erreur interne non classee; ne pas retenter aveuglement un appel non idempotent (la recherche est en lecture seule) |

Une erreur `DataAccessException` est traduite en `503`; les autres erreurs inattendues ne sont pas converties en message contenant leurs details internes.

## Limites du modele et de l'exploitation

- Le modele actuel de la recherche structurée permet nom, prenom, matricule, sexe, age, date de naissance, date d'embauche, poste, service, direction, categorie, corps, type d'emploi, classe et echelon. Il ne permet pas un filtre `statut` dans cette requete.
- Les comparaisons sont securisees contre l'injection SQL par whitelist et parametres lies. Une recherche approximative utilise `pg_trgm`; une migration d'index trigramme existe pour nom, prenom, poste, service et direction. Les expressions concatenees et les autres criteres n'ont pas tous un index correspondant. Un grand `offset` peut etre couteux; la limite de 10 000 borne les lignes sautees.
- Le depot contient des migrations SQL mais n'inclut pas Flyway/Liquibase dans `pom.xml`; leur execution n'est donc pas demontree par le demarrage de l'application. Le schema de production et les plans `EXPLAIN ANALYZE` n'ont pas pu etre verifies depuis le depot seul.
- Aucun delai d'execution SQL applicatif n'est configure ici. Bandy Akama doit appliquer des delais de connexion et de lecture a ses appels HTTP; le `statement_timeout` PostgreSQL doit etre defini en deploiement apres mesure sur le schema et le volume reels.
- Les operations qui modifient des donnees restent les endpoints metier ordinaires, proteges par le bearer utilisateur et leurs controles de service/audit existants. Aucun outil d'ecriture IA dedie n'est declare par cette API.

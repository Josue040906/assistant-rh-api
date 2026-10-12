# Localités de Madagascar et numéros de téléphone

## Source géographique

Le référentiel importé est le jeu **Madagascar - Subnational Administrative
Boundaries (COD-AB)** publié par OCHA sur HDX :

- Fiche et métadonnées : <https://data.humdata.org/dataset/cod-ab-mdg>
- Classeur XLSX des codes et noms :
  <https://data.humdata.org/dataset/26fa506b-0727-4d9d-a590-d2abee21ee22/resource/44c25ef4-e858-48e3-8d93-f0d4d08bdc9c/download/mdg_admin_boundaries.xlsx>
- GeoJSON des limites :
  <https://data.humdata.org/dataset/26fa506b-0727-4d9d-a590-d2abee21ee22/resource/4cefb60b-0986-4a91-8186-70ef5730ede6/download/mdg_admin_boundaries.geojson.zip>
- Licence indiquée par HDX : **CC BY 3.0 IGO**. Conserver l'attribution
  « BNGRC / OCHA, Madagascar COD-AB » et cette licence lors de toute
  redistribution.

La fiche attribue la création des limites au BNGRC au 1 août 2009, leur
publication/dernière édition avant publication au 10 août 2018, et indique une
revue HDX le 6 juillet 2026. Cette revue ne signifie pas que les limites
administratives ont été redessinées en 2026. Le classeur téléchargé a été
contrôlé : il contient 24 régions, 120 districts, 1 645 communes et 17 465
fokontany, avec des P-codes parentaux. L'importeur vérifie ces effectifs,
l'unicité des codes et les rattachements à chaque exécution.

Ce référentiel n'est pas une preuve d'exhaustivité actuelle des fokontany.
Il ne fournit pas de couverture nationale garantie des quartiers plus petits.
Les « fokontany » ci-dessous désignent donc le niveau admin 4 du jeu, pas
chaque quartier ou lieu-dit. Les limites géométriques ne sont pas stockées par
cette fonctionnalité : seules les unités, leurs noms, codes et relations sont
importés. Les entrées ajoutées manuellement sont distinguées par `origine =
'MANUEL'`; aucune API publique de création manuelle n'est exposée.

## Migration et import sous Windows PowerShell

Prérequis : Python 3.10+ et PostgreSQL avec `psql`. La migration ajoute la
table `localite`, ses index et une FK nullable `employe.localite_residence_id`.
Elle ne supprime ni ne réécrit aucune adresse ou donnée d'agent existante.

Depuis la racine du dépôt :

```powershell
$psql = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
& $psql -w -v ON_ERROR_STOP=1 -h localhost -p 5433 -U postgres -d assistant_rh -f "src\main\resources\db\migration\V20261009_01__localites_madagascar.sql"

python scripts\import_localites_madagascar.py --psql $psql
```

L'importeur télécharge le classeur depuis l'URL HDX ci-dessus, vérifie les
colonnes, les effectifs, les codes dupliqués et les codes parentaux, convertit
les dates Excel et applique les données en une seule transaction PostgreSQL.
Il fait un upsert sur `(source_name, source_code)` : il peut être rejoué sans
dupliquer les localités. Une erreur de source ou de base retourne un code
d'échec et annule la transaction. Il ne supprime pas d'anciennes lignes
importées qui ne figureraient plus dans une version future de la source.

Pour valider une copie téléchargée hors ligne :

```powershell
$source = ".\data\mdg_admin_boundaries.xlsx"
python scripts\import_localites_madagascar.py --xlsx $source --validate-only
python scripts\import_localites_madagascar.py --xlsx $source --psql $psql
```

La migration n'est pas lancée automatiquement au démarrage Spring : appliquez
le SQL explicitement une seule fois par base, puis exécutez l'import. Pour
démarrer l'application, configurez ses variables requises comme d'habitude :

```powershell
$env:JWT_SECRET = "<secret configure pour le projet>"
$env:GEMINI_API_KEY = "<cle configuree pour le projet>"
$env:TELEPHONE_REGION_PAR_DEFAUT = "MG"
.\mvnw.cmd spring-boot:run
```

## API de consultation

Les réponses de liste sont des tableaux de localités :

```json
[
  {
    "id": 1,
    "niveau": "REGION",
    "nom": "Analamanga",
    "parentId": null,
    "codeSource": "MG11",
    "origine": "HDX_OCHA"
  }
]
```

Endpoints :

| Requête | Réponse |
|---|---|
| `GET /api/localites/regions` | Tableau des régions |
| `GET /api/localites/districts?regionId=1` | Tableau des districts de cette région |
| `GET /api/localites/communes?districtId=2` | Tableau des communes de ce district |
| `GET /api/localites/quartiers?communeId=3` | Fokontany enregistrés pour cette commune (tableau vide si aucun) |
| `GET /api/localites/recherche?q=Anta&page=0&size=20` | `{ "page": 0, "size": 20, "total": 1, "resultats": [...] }` |
| `GET /api/localites/123` | `{ "localite": {...}, "cheminAdministratif": [region, district, commune, fokontany] }` |

La taille de page est comprise entre 1 et 100, avec 20 par défaut. Les
recherches sont partielles et insensibles à la casse. Les identifiants parentaux
sont contrôlés : demander des districts pour un identifiant qui n'est pas une
région retourne une erreur HTTP 400. Ces référentiels sont des données
administratives de lecture ; toute requête mutante reste soumise à
l'authentification existante.

Exemples PowerShell :

```powershell
curl.exe "http://localhost:8080/api/localites/regions"
curl.exe "http://localhost:8080/api/localites/districts?regionId=1"
curl.exe "http://localhost:8080/api/localites/recherche?q=Anta&page=0&size=20"
curl.exe "http://localhost:8080/api/localites/123"
```

## Résidence dans le profil agent

Les routes de profil et d'agent existantes sont conservées. Les représentations
agent/profil retournent désormais `localite_residence_id`,
`localite_residence_nom` et `localite_residence_niveau`; le chemin complet est
récupérable avec `GET /api/localites/{id}`. Les anciennes valeurs `adresse`
restent inchangées et éditables : aucune correspondance automatique n'est
tentée, car une adresse libre historique ne permet pas d'identifier de manière
fiable une unité administrative.

Une mise à jour de profil peut joindre l'identifiant d'une commune ou d'un
fokontany existant :

```json
{
  "userId": 9,
  "nom": "Rakoto",
  "prenom": "Hery",
  "sexe": "M",
  "cin": "101234567890",
  "dateNaissance": "1990-01-02",
  "lieuNaissance": "Antananarivo",
  "adresse": "Adresse détaillée historique",
  "telephone": "034 12 345 67",
  "localiteResidenceId": 123
}
```

Pour les clients existants qui n'envoient pas `localiteResidenceId`, une
résidence déjà sélectionnée est conservée. Pour effacer explicitement la
référence, envoyer `"effacerLocaliteResidence": true`. Fournir simultanément
une référence et ce drapeau retourne HTTP 400. À la création agent, le champ
`localiteResidenceId` est facultatif.

Le frontend peut charger les menus en cascade région → district → commune →
fokontany. Lors d'un changement de parent, il doit réinitialiser les choix
enfants puis les recharger depuis l'API. Le backend n'accepte qu'une seule
référence terminale (commune ou fokontany), empêchant une combinaison
d'identifiants parentaux incohérents.

## Téléphones

Google libphonenumber valide les numéros avec `MG` comme région par défaut
(`TELEPHONE_REGION_PAR_DEFAUT` configurable). La saisie nationale
`034 12 345 67` et la saisie internationale `+261 34 12 345 67` sont
normalisées en `+261341234567`; un numéro étranger valide fourni en format
international est conservé au format E.164. Les numéros invalides reçoivent
HTTP 400 avec un message compréhensible. Un champ vide reste facultatif (`null`).
Cette validation porte sur le plan de numérotation, pas sur la propriété ou
l'accessibilité réelle du numéro.

Les enregistrements préexistants ne sont pas modifiés à la migration. Ils
seront normalisés après une mise à jour contenant un numéro valide ; le type
PostgreSQL reste `VARCHAR(30)`, suffisant pour E.164.

## Tests

Depuis la racine du dépôt :

```powershell
$env:GEMINI_API_KEY = "<cle configuree pour le projet>"
$env:JWT_SECRET = "<secret configure pour le projet>"
.\mvnw.cmd test
python -m py_compile scripts\import_localites_madagascar.py
```

Les tests couvrent les formats téléphoniques, les rejets de numéro et
d'identifiant incohérents, les réponses des endpoints et les profils. Le
contrat d'import vérifie l'upsert transactionnel. L'import réel du classeur a
également été exécuté deux fois sur la base de développement : 24 régions,
120 districts, 1 645 communes, 17 465 fokontany et zéro parent incohérent ;
le nombre de lignes n'a pas augmenté au second passage.

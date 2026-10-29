# Sécurité et autorisation de l'API

## Authentification par défaut

Toutes les routes `/api/**` exigent un jeton Bearer valide et un compte SPERS actif.
Seules les routes `POST /api/utilisateurs/login` et
`POST /api/utilisateurs/inscription` restent publiques. Les requêtes CORS `OPTIONS`
sont laissées passer pour le précontrôle navigateur. Les fichiers sous
`/uploads/employes/**` restent protégés par le même contrôle.

La politique est volontairement fermée par défaut : une nouvelle route API ne devient
pas publique simplement parce qu'elle a été oubliée dans une liste de routes protégées.

## Propriété du compte

Les opérations de consultation et de modification du profil, de téléversement de la photo
de profil et de changement de mot de passe utilisent l'identifiant du compte extrait du
jeton vérifié, et non un identifiant fourni par le client. Cette règle évite qu'un appelant
puisse viser un autre compte en changeant un identifiant dans l'URL, les paramètres ou le
corps JSON.

## Limite importante

L'authentification n'est pas une matrice complète de permissions. Les droits par opération
(par exemple quelles fonctions RH sont réservées à `SPERS_CHEF` et lesquelles peuvent être
utilisées par `SPERS_AGENT`) doivent encore être explicitement définis, appliqués et testés
sur chaque endpoint sensible. Ne pas activer de nouveaux outils de modification dans Bandy
Akama avant cette vérification.

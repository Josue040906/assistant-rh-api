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

## Autorisation centralisée par rôle

Les contrôleurs peuvent déclarer une opération réservée à un chef avec l'annotation
`@RequiresSpersChef`. L'intercepteur `RoleAuthorizationInterceptor` applique cette
restriction à partir de l'identité vérifiée par l'intercepteur d'authentification :
l'absence d'identité entraîne un refus (401) et un compte qui n'est pas chef actif entraîne
un refus (403). La vérification est faite avant l'entrée dans le contrôleur.

Les opérations actuellement déclarées chef uniquement sont :
- consultation des comptes en attente et approbation/refus des comptes ;
- consultation et modification des règles de reclassement et de l'ordre académique.

Cette liste est le périmètre effectivement annoté, pas encore la matrice complète de
toutes les opérations RH.

## Points restant à auditer

L'authentification n'est pas une matrice complète de permissions. Il reste à vérifier et
à formaliser les droits pour :
- création/modification des agents et de leurs affectations ;
- création/modification/archivage et consultation des documents RH ;
- diplômes, situations de carrière, avancement et reclassement opérationnel ;
- création/modification/suppression des référentiels directions, services, postes,
  grades et statuts ;
- consultation des activités et accès aux données d'agents.

Certains services vérifient déjà qu'un acteur est un agent SPERS actif ou un chef actif,
mais les contrôles sont hétérogènes. Les routes et leurs services doivent être testés
ensemble pour éviter qu'une permission ne dépende uniquement de l'interface utilisateur
ou d'un identifiant d'acteur fourni par le client.

Ne pas activer de nouveaux outils de modification dans Bandy Akama avant la vérification
des droits de l'opération correspondante.

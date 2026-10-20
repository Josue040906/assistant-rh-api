# Notifications in-app

Le backend enregistre des notifications séparées du journal d'activités d'audit.
Elles sont personnelles au compte destinataire et accessibles uniquement aux
utilisateurs SPERS actifs.

## Événements

- `NOUVEL_AGENT` : lors de la création transactionnelle d'un agent, tous les
  comptes SPERS actifs reçoivent le nom et le matricule de l'agent ainsi que le
  nom (ou l'e-mail si son nom n'est pas disponible) du créateur.
- `ANCIENNETE_UN_MOIS` : lorsque la date d'éligibilité à l'échelon suivant,
  calculée à partir de la situation de carrière actuelle et de `echelon.duree_min`,
  tombe dans le mois à venir.
- `ANCIENNETE_ATTEINTE` : lorsque cette date d'éligibilité est atteinte ou
  dépassée. Les comptes SPERS actifs sont destinataires.

La vérification d'ancienneté s'exécute chaque jour à 08:00 dans le fuseau
`Indian/Antananarivo`. Elle est réglable avec `NOTIFICATIONS_ANCIENNETE_CRON`
(syntaxe cron Spring) et `NOTIFICATIONS_ZONE`. Une clé unique par destinataire,
événement et situation empêche les doublons lors des exécutions répétées.

## API

Toutes les routes exigent un jeton Bearer valide et un compte SPERS actif. Le
destinataire est toujours déterminé depuis le jeton; aucun identifiant de
destinataire ne peut être fourni par le client.

- `GET /api/notifications?nonLuesSeulement=false&limite=50` retourne
  `{ "notifications": [...], "nonLues": 3 }`. `limite` est comprise entre 1 et
  100. Chaque élément contient `id`, `type`, `titre`, `message`, `employeId`,
  `acteurId`, `creeLe` et `lueLe`.
- `PATCH /api/notifications/{id}/lue` marque comme lue une notification
  appartenant au compte connecté. Réponses : `204` en cas de mise à jour,
  `404` si elle n'existe pas ou appartient à un autre compte.
- `PATCH /api/notifications/lues` marque toutes les notifications du compte
  connecté comme lues et retourne `{ "modifiees": n }`.

## Mise en base

Appliquer `src/main/resources/db/migration/V20261020_02__notifications_in_app.sql`
à la base RH avant le déploiement. Le projet n'embarque pas de moteur Flyway ou
Liquibase ; les scripts de migration de ce dépôt doivent être appliqués par
l'outil de déploiement habituel.

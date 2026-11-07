-- Requêtes d'écriture rejouables sans double effet (en-tête Idempotency-Key).
-- Une ligne sans statut_http est une requête en cours ; seules les réussites
-- (2xx) sont conservées, avec la réponse à renvoyer à l'identique.
CREATE TABLE IF NOT EXISTS requete_idempotente (
    utilisateur_id INTEGER NOT NULL,
    cle VARCHAR(100) NOT NULL,
    route VARCHAR(200) NOT NULL,
    empreinte CHAR(64) NOT NULL,
    statut_http INTEGER,
    reponse TEXT,
    cree_le TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (utilisateur_id, cle)
);

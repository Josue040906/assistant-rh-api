-- Les e-mails aux agents. Un événement (mutation, avancement, demande acceptée,
-- ancienneté ou retraite qui approche) prépare un brouillon PROPOSE ; un agent du
-- Service du Personnel décide : il l'envoie (ENVOYE, ou ECHEC si le fournisseur
-- refuse) ou l'écarte (ECARTE). Rien ne part sans cette décision.
CREATE TABLE message_agent (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employe_id INTEGER NOT NULL REFERENCES employe(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    sujet VARCHAR(200) NOT NULL,
    corps TEXT NOT NULL,
    statut VARCHAR(20) NOT NULL DEFAULT 'PROPOSE'
        CHECK (statut IN ('PROPOSE', 'ENVOYE', 'ECARTE', 'ECHEC')),
    -- Un événement ne prépare qu'un brouillon (même clé que sa notification, quand il en a une).
    cle_deduplication VARCHAR(160) NOT NULL UNIQUE,
    cree_le TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- L'envoi : à quelle adresse, par qui, quand, et ce que le fournisseur a répondu.
    adresse VARCHAR(150),
    traite_par INTEGER REFERENCES utilisateur(id) ON DELETE SET NULL,
    traite_le TIMESTAMP,
    erreur TEXT,
    reference_fournisseur VARCHAR(160)
);
CREATE INDEX ix_message_agent_statut ON message_agent (statut, cree_le DESC);
CREATE INDEX ix_message_agent_employe ON message_agent (employe_id, cree_le DESC);

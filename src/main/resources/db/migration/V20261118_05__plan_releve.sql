-- Le plan de relève d'un poste : le successeur pressenti, où en est sa
-- préparation et une note (« à former à la paie avant 2027 »). Un poste a au
-- plus un plan ; le désigner ne mute personne, c'est une intention suivie.
CREATE TABLE plan_releve (
    poste_id INTEGER PRIMARY KEY REFERENCES poste(id) ON DELETE CASCADE,
    successeur_id INTEGER NOT NULL REFERENCES employe(id) ON DELETE CASCADE,
    statut VARCHAR(20) NOT NULL DEFAULT 'A_PREPARER'
        CHECK (statut IN ('A_PREPARER', 'EN_PREPARATION', 'PRET')),
    note VARCHAR(500),
    designe_par INTEGER REFERENCES utilisateur(id) ON DELETE SET NULL,
    designe_le TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modifie_le TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_plan_releve_successeur ON plan_releve (successeur_id);

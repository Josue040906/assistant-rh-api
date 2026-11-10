-- Lot 5 : congés. Les droits s'acquièrent chaque mois complet depuis le
-- 1er janvier (ou l'embauche, si elle est plus récente) et repartent à zéro au
-- 1er janvier suivant. Le rythme est une donnée du type de document CONGE.

ALTER TABLE type_document ADD COLUMN IF NOT EXISTS jours_conge_par_mois NUMERIC(4, 2)
    CHECK (jours_conge_par_mois IS NULL OR jours_conge_par_mois > 0);
UPDATE type_document SET jours_conge_par_mois = 2.5 WHERE code = 'CONGE';

-- Chaque absence réelle : une demande de congé appliquée (ou une saisie directe).
CREATE TABLE IF NOT EXISTS conge (
    id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employe_id    INTEGER NOT NULL REFERENCES employe(id),
    document_id   INTEGER UNIQUE REFERENCES document(id),
    date_debut    DATE    NOT NULL,
    date_fin      DATE    NOT NULL,
    jours         INTEGER NOT NULL CHECK (jours > 0),
    date_creation TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_conge_dates CHECK (date_fin >= date_debut)
);

CREATE INDEX IF NOT EXISTS idx_conge_employe_dates ON conge (employe_id, date_debut, date_fin);

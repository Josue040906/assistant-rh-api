-- Simplifie l'historique métier : la catégorie cible est liée à l'activité
-- qui fournit l'auteur et l'horodatage. Les migrations précédentes restent immuables.

ALTER TABLE reclassement
    ADD COLUMN IF NOT EXISTS categorie_id INTEGER;

-- Préserve les anciennes entrées éventuelles avant de retirer categorie_apres_id.
UPDATE reclassement
SET categorie_id = categorie_apres_id
WHERE categorie_id IS NULL;

DROP INDEX IF EXISTS idx_reclassement_employe_date;
DROP INDEX IF EXISTS idx_reclassement_diplome;

ALTER TABLE reclassement
    DROP CONSTRAINT IF EXISTS ck_reclassement_changement_classification,
    DROP CONSTRAINT IF EXISTS fk_reclassement_employe_diplome,
    DROP CONSTRAINT IF EXISTS fk_reclassement_historique_avant,
    DROP CONSTRAINT IF EXISTS fk_reclassement_historique_apres,
    DROP CONSTRAINT IF EXISTS fk_reclassement_categorie_avant,
    DROP CONSTRAINT IF EXISTS fk_reclassement_categorie_apres,
    DROP CONSTRAINT IF EXISTS fk_reclassement_corps_avant,
    DROP CONSTRAINT IF EXISTS fk_reclassement_corps_apres,
    DROP CONSTRAINT IF EXISTS fk_reclassement_echelon_avant,
    DROP CONSTRAINT IF EXISTS fk_reclassement_echelon_apres;

ALTER TABLE reclassement
    DROP COLUMN IF EXISTS employe_diplome_id,
    DROP COLUMN IF EXISTS historique_avant_id,
    DROP COLUMN IF EXISTS historique_apres_id,
    DROP COLUMN IF EXISTS categorie_avant_id,
    DROP COLUMN IF EXISTS categorie_apres_id,
    DROP COLUMN IF EXISTS corps_avant_id,
    DROP COLUMN IF EXISTS corps_apres_id,
    DROP COLUMN IF EXISTS echelon_avant_id,
    DROP COLUMN IF EXISTS echelon_apres_id,
    DROP COLUMN IF EXISTS date_effet,
    DROP COLUMN IF EXISTS date_creation;

ALTER TABLE reclassement
    ALTER COLUMN categorie_id SET NOT NULL,
    ADD CONSTRAINT fk_reclassement_categorie
        FOREIGN KEY (categorie_id) REFERENCES categorie(id);

CREATE INDEX idx_reclassement_employe
    ON reclassement (employe_id, id_activite DESC);

COMMENT ON TABLE reclassement IS
    'Historique des changements de catégorie; l''activité liée fournit l''auteur et l''horodatage.';
COMMENT ON COLUMN reclassement.categorie_id IS
    'Catégorie attribuée lors du reclassement.';

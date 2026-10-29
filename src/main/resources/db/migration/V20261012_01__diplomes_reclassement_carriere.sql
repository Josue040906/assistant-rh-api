-- Adds auditable career snapshots and diploma ranking used by automatic reclassification.
-- Existing closed career periods are deliberately left unchanged because their
-- historical category cannot be inferred reliably from the agent's current category.

ALTER TABLE historique_carriere
    ADD COLUMN IF NOT EXISTS categorie_id INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_historique_carriere_categorie'
          AND conrelid = 'historique_carriere'::regclass
    ) THEN
        ALTER TABLE historique_carriere
            ADD CONSTRAINT fk_historique_carriere_categorie
            FOREIGN KEY (categorie_id) REFERENCES categorie(id);
    END IF;
END
$$;

UPDATE historique_carriere hc
SET categorie_id = e.categorie_id
FROM employe e
WHERE hc.employe_id = e.id
  AND hc.date_fin IS NULL
  AND hc.categorie_id IS NULL;

-- Replace the legacy diploma/date uniqueness rule with clearer duplicate checks:
-- distinct institutions or certificate references are allowed for the same date.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_employe_diplome'
          AND conrelid = 'employe_diplome'::regclass
    ) THEN
        ALTER TABLE employe_diplome
            DROP CONSTRAINT uq_employe_diplome;
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_employe_diplome_reference
    ON employe_diplome (employe_id, lower(btrim(numero_diplome)))
    WHERE numero_diplome IS NOT NULL
      AND btrim(numero_diplome) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS uq_employe_diplome_sans_reference
    ON employe_diplome (
        employe_id,
        diplome_id,
        date_obtention,
        lower(btrim(COALESCE(etablissement, ''))),
        lower(btrim(COALESCE(mention, '')))
    )
    WHERE numero_diplome IS NULL
       OR btrim(numero_diplome) = '';

ALTER TABLE niveau_diplome
    ADD COLUMN IF NOT EXISTS ordre_academique INTEGER;

COMMENT ON COLUMN niveau_diplome.ordre_academique IS
    'Classement académique utilisé pour identifier le diplôme de référence; les valeurs les plus élevées représentent les niveaux supérieurs.';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_niveau_diplome_ordre_academique'
          AND conrelid = 'niveau_diplome'::regclass
    ) THEN
        ALTER TABLE niveau_diplome
            ADD CONSTRAINT ck_niveau_diplome_ordre_academique
            CHECK (ordre_academique IS NULL OR ordre_academique > 0);
    END IF;
END
$$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_echelon_classe_id
    ON echelon (classe_id, id);


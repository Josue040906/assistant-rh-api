-- Conserve aussi la situation d'échelon avant/après.
-- Permet d'enregistrer un reclassement lorsque la catégorie reste identique,
-- mais que la règle liée au diplôme impose un autre échelon.
ALTER TABLE reclassement
    ADD COLUMN IF NOT EXISTS echelon_avant_id INTEGER,
    ADD COLUMN IF NOT EXISTS echelon_apres_id INTEGER;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_reclassement_echelon_avant'
          AND conrelid = 'reclassement'::regclass
    ) THEN
        ALTER TABLE reclassement
            ADD CONSTRAINT fk_reclassement_echelon_avant
            FOREIGN KEY (echelon_avant_id) REFERENCES echelon(id);
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_reclassement_echelon_apres'
          AND conrelid = 'reclassement'::regclass
    ) THEN
        ALTER TABLE reclassement
            ADD CONSTRAINT fk_reclassement_echelon_apres
            FOREIGN KEY (echelon_apres_id) REFERENCES echelon(id);
    END IF;
END $$;

ALTER TABLE reclassement
    DROP CONSTRAINT IF EXISTS ck_reclassement_changement_classification;

ALTER TABLE reclassement
    ADD CONSTRAINT ck_reclassement_changement_classification
    CHECK (
        categorie_avant_id <> categorie_apres_id
        OR corps_avant_id IS DISTINCT FROM corps_apres_id
        OR echelon_avant_id IS DISTINCT FROM echelon_apres_id
    );

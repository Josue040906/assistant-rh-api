-- Diploma entries entered through the new form are defined by normalized
-- level, study-domain, institution, and optional mention references.
ALTER TABLE employe_diplome
    ALTER COLUMN diplome_id DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_employe_diplome_normalise_sans_reference
    ON employe_diplome (
        employe_id,
        niveau_id,
        domaine_id,
        date_obtention,
        etablissement_id,
        COALESCE(mention_id, 0)
    )
    WHERE diplome_id IS NULL
      AND niveau_id IS NOT NULL
      AND domaine_id IS NOT NULL
      AND etablissement_id IS NOT NULL
      AND date_obtention IS NOT NULL
      AND (numero_diplome IS NULL OR btrim(numero_diplome) = '');

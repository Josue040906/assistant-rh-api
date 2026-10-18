-- Preserve employee diploma history using the normalized references before
-- removing the legacy diploma catalogue.
UPDATE employe_diplome ed
SET niveau_id = COALESCE(ed.niveau_id, d.niveau_id),
    domaine_id = COALESCE(
        ed.domaine_id,
        (
            SELECT dom.id
            FROM domaine_etude dom
            WHERE lower(btrim(dom.libelle)) = lower(btrim(d.domaine))
        )
    ),
    etablissement_id = COALESCE(
        ed.etablissement_id,
        (
            SELECT e.id
            FROM etablissement e
            WHERE lower(btrim(e.nom)) = lower(btrim(ed.etablissement))
        )
    ),
    mention_id = COALESCE(
        ed.mention_id,
        (
            SELECT m.id
            FROM mention m
            WHERE lower(btrim(m.libelle)) = lower(btrim(ed.mention))
        )
    )
FROM diplome d
WHERE ed.diplome_id = d.id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM employe_diplome
        WHERE niveau_id IS NULL
           OR domaine_id IS NULL
           OR etablissement_id IS NULL
           OR date_obtention IS NULL
    ) THEN
        RAISE EXCEPTION
            'Cannot remove the legacy diploma catalogue: employee diploma history has incomplete normalized references.';
    END IF;
END
$$;

DROP INDEX IF EXISTS uq_employe_diplome_normalise_sans_reference;
DROP INDEX IF EXISTS uq_employe_diplome_sans_reference;

CREATE UNIQUE INDEX uq_employe_diplome_normalise_sans_reference
    ON employe_diplome (
        employe_id,
        niveau_id,
        domaine_id,
        date_obtention,
        etablissement_id,
        COALESCE(mention_id, 0)
    )
    WHERE numero_diplome IS NULL
       OR btrim(numero_diplome) = '';

ALTER TABLE employe_diplome
    ALTER COLUMN niveau_id SET NOT NULL,
    ALTER COLUMN domaine_id SET NOT NULL,
    ALTER COLUMN etablissement_id SET NOT NULL,
    ALTER COLUMN date_obtention SET NOT NULL,
    DROP CONSTRAINT IF EXISTS fk_employe_diplome_diplome,
    DROP COLUMN diplome_id,
    DROP COLUMN etablissement,
    DROP COLUMN mention;

DROP TABLE diplome;

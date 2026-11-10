-- Lot 2 : chaque type d'emploi a son parcours ; un grade est une position
-- (une ligne echelon) dans une classe de ce parcours. Données fictives.

-- 1. Types d'emploi : Fonctionnaire, Agent contractuel, Stagiaire, rien d'autre.
DELETE FROM type_emploi
WHERE nom IN ('Agent temporaire', 'Personnel d''appui')
  AND NOT EXISTS (SELECT 1 FROM employe e WHERE e.type_emploi_id = type_emploi.id);
INSERT INTO type_emploi (nom) VALUES ('Stagiaire') ON CONFLICT (nom) DO NOTHING;

-- 2. Parcours, chacun rattaché à un type d'emploi.
CREATE TABLE IF NOT EXISTS parcours (
    id             INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code           VARCHAR(50)  NOT NULL UNIQUE,
    libelle        VARCHAR(200) NOT NULL,
    type_emploi_id INTEGER      NOT NULL REFERENCES type_emploi(id)
);

INSERT INTO parcours (code, libelle, type_emploi_id)
SELECT v.code, v.libelle, te.id
FROM (VALUES
    ('FONCTIONNAIRE', 'Classes et échelons', 'Fonctionnaire'),
    ('AUXILIAIRE_ECHELLE_I', 'Auxiliaire, échelle I', 'Agent contractuel'),
    ('STAGE', 'Stage', 'Stagiaire')
) AS v(code, libelle, type_nom)
JOIN type_emploi te ON te.nom = v.type_nom
ON CONFLICT (code) DO NOTHING;

-- 3. Chaque classe appartient à un parcours ; les classes existantes sont celles des fonctionnaires.
ALTER TABLE classe ADD COLUMN IF NOT EXISTS parcours_id INTEGER REFERENCES parcours(id);
UPDATE classe SET parcours_id = (SELECT id FROM parcours WHERE code = 'FONCTIONNAIRE')
WHERE parcours_id IS NULL;

INSERT INTO classe (libelle, ordre, parcours_id)
SELECT v.libelle, v.ordre, p.id
FROM (VALUES
    ('Auxiliaire échelle I', 101, 'AUXILIAIRE_ECHELLE_I'),
    ('Stage', 201, 'STAGE')
) AS v(libelle, ordre, parcours_code)
JOIN parcours p ON p.code = v.parcours_code
WHERE NOT EXISTS (SELECT 1 FROM classe c WHERE c.libelle = v.libelle);

ALTER TABLE classe ALTER COLUMN parcours_id SET NOT NULL;

-- 4. Les grades : neuf échelons d'auxiliaire (2 ans, fictif), une position de stage d'un an.
INSERT INTO echelon (classe_id, ordre, duree_min)
SELECT c.id, g.ordre, 2
FROM classe c CROSS JOIN generate_series(1, 9) AS g(ordre)
WHERE c.libelle = 'Auxiliaire échelle I'
  AND NOT EXISTS (SELECT 1 FROM echelon e WHERE e.classe_id = c.id AND e.ordre = g.ordre);

INSERT INTO echelon (classe_id, ordre, duree_min)
SELECT c.id, 1, 1
FROM classe c
WHERE c.libelle = 'Stage'
  AND NOT EXISTS (SELECT 1 FROM echelon e WHERE e.classe_id = c.id AND e.ordre = 1);

-- 5. Chaque grade a un code court (3C2E) et un libellé que tout le monde comprend.
ALTER TABLE echelon ADD COLUMN IF NOT EXISTS code VARCHAR(20);
ALTER TABLE echelon ADD COLUMN IF NOT EXISTS libelle VARCHAR(200);

WITH prefixe(classe, code, nom) AS (VALUES
    ('3e classe', '3C', '3e classe'),
    ('2e classe', '2C', '2e classe'),
    ('1re classe', '1C', '1re classe'),
    ('Principale', 'PC', 'Classe principale'),
    ('Exceptionnelle', 'EC', 'Classe exceptionnelle'),
    ('Auxiliaire échelle I', 'A1', 'Auxiliaire échelle I'),
    ('Stage', 'ST0', 'Stage, position 0')
)
UPDATE echelon e
SET code = p.code || e.ordre || 'E',
    libelle = CASE
        WHEN c.libelle = 'Stage' THEN p.nom || e.ordre || ', échelon unique'
        ELSE p.nom || ', ' || e.ordre || CASE WHEN e.ordre = 1 THEN 'er' ELSE 'e' END || ' échelon'
    END
FROM classe c
JOIN prefixe p ON p.classe = c.libelle
WHERE c.id = e.classe_id
  AND e.code IS NULL;

ALTER TABLE echelon ALTER COLUMN code SET NOT NULL;
ALTER TABLE echelon ALTER COLUMN libelle SET NOT NULL;
ALTER TABLE echelon DROP CONSTRAINT IF EXISTS uq_echelon_code;
ALTER TABLE echelon ADD CONSTRAINT uq_echelon_code UNIQUE (code);

-- 6. Le statut d'agent faisait doublon avec le type d'emploi.
DROP TABLE IF EXISTS statut_agent;

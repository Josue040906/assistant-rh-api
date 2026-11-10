-- Lot 3 : grille indiciaire, catégorie × grade → indice. Valeurs ENTIÈREMENT
-- FICTIVES : celles de l'encadreur (catégories I à IV), inversées pour que
-- l'indice croisse avec la catégorie, puis prolongées jusqu'à la catégorie X
-- et au dernier grade de chaque parcours. Seules les données font foi : le
-- code lit l'indice, il ne le calcule jamais.

CREATE TABLE IF NOT EXISTS grille_indiciaire (
    categorie_id INTEGER NOT NULL REFERENCES categorie(id),
    echelon_id   INTEGER NOT NULL REFERENCES echelon(id),
    indice       INTEGER NOT NULL CHECK (indice > 0),
    PRIMARY KEY (categorie_id, echelon_id)
);

WITH rang_categorie(code, rang) AS (VALUES
    ('I', 1), ('II', 2), ('III', 3), ('IV', 4), ('V', 5),
    ('VI', 6), ('VII', 7), ('VIII', 8), ('IX', 9), ('X', 10)
),
-- base : indice de la catégorie I au premier grade ; ecart_categorie et pas_grade
-- reprennent les écarts des grilles fournies (cinq premiers grades), pas_prolongement
-- vaut au-delà. Auxiliaire : +12 après A15E, pour qu'aucun contractuel ne dépasse
-- le premier grade de fonctionnaire de sa catégorie (A19E cat. I = 348 < 3C1E = 350).
regle(parcours_code, base, ecart_categorie, pas_grade, pas_prolongement) AS (VALUES
    ('FONCTIONNAIRE', 350, 50, 20, 20),
    ('AUXILIAIRE_ECHELLE_I', 240, 20, 15, 12),
    ('STAGE', 150, 10, 0, 0)
),
grade AS (
    SELECT ec.id AS echelon_id, pa.code AS parcours_code,
           ROW_NUMBER() OVER (PARTITION BY pa.id ORDER BY cl.ordre, ec.ordre) AS rang
    FROM echelon ec
    JOIN classe cl ON cl.id = ec.classe_id
    JOIN parcours pa ON pa.id = cl.parcours_id
)
INSERT INTO grille_indiciaire (categorie_id, echelon_id, indice)
SELECT ca.id, g.echelon_id,
       r.base + r.ecart_categorie * (rc.rang - 1)
              + r.pas_grade * (LEAST(g.rang, 5) - 1)
              + r.pas_prolongement * GREATEST(g.rang - 5, 0)
FROM categorie ca
JOIN rang_categorie rc ON rc.code = ca.code
CROSS JOIN grade g
JOIN regle r ON r.parcours_code = g.parcours_code
ON CONFLICT (categorie_id, echelon_id) DO NOTHING;

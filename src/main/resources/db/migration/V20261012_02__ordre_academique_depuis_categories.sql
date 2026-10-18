-- The already-approved level_diploma -> category reference assigns categories I
-- through VI in descending academic order. Preserve explicit administrator
-- choices and fill only the newly added, still-unconfigured ranking values.
UPDATE niveau_diplome n
SET ordre_academique = CASE c.code
    WHEN 'VI' THEN 1
    WHEN 'V' THEN 2
    WHEN 'IV' THEN 3
    WHEN 'III' THEN 4
    WHEN 'II' THEN 5
    WHEN 'I' THEN 6
    ELSE NULL
END
FROM categorie c
WHERE c.id = n.categorie_id
  AND n.ordre_academique IS NULL
  AND c.code IN ('I', 'II', 'III', 'IV', 'V', 'VI');

CREATE UNIQUE INDEX IF NOT EXISTS uq_niveau_diplome_ordre_academique
    ON niveau_diplome (ordre_academique)
    WHERE ordre_academique IS NOT NULL;

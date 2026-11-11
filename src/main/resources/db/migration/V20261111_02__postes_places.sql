-- Chantier « postes » : un poste est un intitulé dans un service, avec un nombre
-- de places (une donnée). Il est occupé par les affectations en cours ; ses places
-- libres et ce qui va se libérer se calculent. Départ : l'occupation actuelle,
-- pour qu'aucun poste n'apparaisse en surnombre.

ALTER TABLE poste ADD COLUMN IF NOT EXISTS nombre_places INTEGER NOT NULL DEFAULT 1
    CHECK (nombre_places > 0);

UPDATE poste p
SET nombre_places = GREATEST(1, (
    SELECT COUNT(*) FROM affectation a
    JOIN employe e ON e.id = a.employe_id
    WHERE a.poste_id = p.id AND a.date_fin IS NULL AND e.date_retraite IS NULL
));

-- Le décideur auquel s'adresse chaque type de demande : un poste (sa fonction
-- est écrite dans la lettre, « Chef du Service du Personnel »), pas une
-- personne, pour qu'une lettre reste juste quand le titulaire change.
-- Le formulaire le propose et l'on peut en choisir un autre.
ALTER TABLE type_document
    ADD COLUMN destinataire_poste_id INTEGER REFERENCES poste(id) ON DELETE SET NULL;

-- Jusqu'ici, toutes les demandes s'adressaient au chef du Service du Personnel.
UPDATE type_document
SET destinataire_poste_id = (
    SELECT p.id
    FROM poste p
    JOIN service s ON s.id = p.service_id
    WHERE s.code = 'SPERS' AND p.intitule ILIKE 'chef de service%'
    ORDER BY p.id
    LIMIT 1
);

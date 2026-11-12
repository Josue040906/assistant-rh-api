-- L'âge minimum pour être embauché est une donnée, par type d'emploi, et non
-- une règle écrite dans le code : un agent doit avoir au moins cet âge à sa
-- date d'embauche (et donc, embauché, ne peut pas être mineur).

ALTER TABLE type_emploi
    ADD COLUMN age_minimum_embauche INTEGER CHECK (age_minimum_embauche > 0);

UPDATE type_emploi SET age_minimum_embauche = 18;

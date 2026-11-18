-- L'adresse e-mail d'un agent, à laquelle l'application pourra le prévenir
-- (avancement possible, mutation appliquée…). Facultative : tous les agents
-- n'en ont pas encore. Deux agents ne partagent pas la même adresse.
ALTER TABLE employe ADD COLUMN email VARCHAR(150);

CREATE UNIQUE INDEX ux_employe_email ON employe (LOWER(email)) WHERE email IS NOT NULL;

-- Ce que le CV d'un agent montre au-delà de sa carrière : situation familiale,
-- langues, compétences et formations continues (distinctes des diplômes).
ALTER TABLE employe
    ADD COLUMN situation_matrimoniale VARCHAR(20)
        CHECK (situation_matrimoniale IN ('CELIBATAIRE', 'MARIE', 'DIVORCE', 'VEUF')),
    ADD COLUMN nombre_enfants SMALLINT CHECK (nombre_enfants BETWEEN 0 AND 30);

CREATE TABLE employe_langue (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employe_id INTEGER NOT NULL REFERENCES employe(id) ON DELETE CASCADE,
    langue VARCHAR(40) NOT NULL,
    niveau VARCHAR(20) NOT NULL CHECK (niveau IN ('MATERNELLE', 'COURANT', 'INTERMEDIAIRE', 'NOTIONS'))
);
CREATE UNIQUE INDEX ux_employe_langue ON employe_langue (employe_id, LOWER(langue));

CREATE TABLE employe_competence (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employe_id INTEGER NOT NULL REFERENCES employe(id) ON DELETE CASCADE,
    libelle VARCHAR(80) NOT NULL
);
CREATE UNIQUE INDEX ux_employe_competence ON employe_competence (employe_id, LOWER(libelle));

CREATE TABLE employe_formation (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    employe_id INTEGER NOT NULL REFERENCES employe(id) ON DELETE CASCADE,
    intitule VARCHAR(200) NOT NULL,
    organisme VARCHAR(150),
    annee SMALLINT CHECK (annee BETWEEN 1950 AND 2100),
    duree_jours SMALLINT CHECK (duree_jours BETWEEN 1 AND 999)
);
CREATE INDEX ix_employe_formation_employe ON employe_formation (employe_id);

-- Les exigences d'un poste, comparées au profil des agents quand on choisit
-- qui l'occupera : une langue (et son niveau minimum), un diplôme (niveau
-- minimum, domaine facultatif), une compétence ou une formation. Chacune est
-- indispensable ou souhaitée. Un conseil : elles ne bloquent aucun choix.
CREATE TABLE poste_exigence (
    id SERIAL PRIMARY KEY,
    poste_id INTEGER NOT NULL REFERENCES poste(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('LANGUE', 'DIPLOME', 'COMPETENCE', 'FORMATION')),
    -- La langue, la compétence, la formation ; pour un diplôme, le domaine (facultatif).
    valeur VARCHAR(150),
    -- Langue : NOTIONS, INTERMEDIAIRE, COURANT, MATERNELLE ; diplôme : code de niveau_diplome.
    niveau VARCHAR(30),
    indispensable BOOLEAN NOT NULL DEFAULT FALSE,
    ordre SMALLINT NOT NULL DEFAULT 0,
    CHECK (type = 'DIPLOME' OR valeur IS NOT NULL),
    CHECK (type <> 'DIPLOME' OR niveau IS NOT NULL)
);
CREATE INDEX ix_poste_exigence_poste ON poste_exigence (poste_id);

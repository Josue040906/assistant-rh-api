-- Pièces scannées (photo ou PDF) d'où viennent un document ou un diplôme :
-- l'original reste consultable par la personne qui vérifie l'enregistrement.
-- Une seule table pour toutes les cibles ; le fichier est rangé dans
-- uploads/pieces, la table n'en garde que le chemin et la description.
CREATE TABLE IF NOT EXISTS piece_scannee (
    id SERIAL PRIMARY KEY,
    cible_type VARCHAR(20) NOT NULL CHECK (cible_type IN ('DOCUMENT', 'DIPLOME')),
    cible_id INTEGER NOT NULL,
    nom_fichier VARCHAR(200) NOT NULL,
    type_mime VARCHAR(100) NOT NULL,
    taille INTEGER NOT NULL,
    chemin VARCHAR(300) NOT NULL,
    depose_par INTEGER,
    depose_le TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_piece_scannee_cible
    ON piece_scannee (cible_type, cible_id);

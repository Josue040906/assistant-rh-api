-- Active la recherche de similarité trigramme PostgreSQL utilisée par le profil agent.
-- À appliquer sur la base assistant_rh avant d'utiliser la recherche approximative.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Index trigrammes pour accélérer les recherches approximatives sur les identités.
CREATE INDEX IF NOT EXISTS idx_employe_nom_trgm
    ON employe USING GIN (LOWER(nom) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_employe_prenom_trgm
    ON employe USING GIN (LOWER(prenom) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_employe_nom_complet_trgm
    ON employe USING GIN (LOWER(CONCAT(prenom, ' ', nom)) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_employe_nom_complet_inverse_trgm
    ON employe USING GIN (LOWER(CONCAT(nom, ' ', prenom)) gin_trgm_ops);

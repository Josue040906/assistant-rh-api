CREATE TABLE IF NOT EXISTS ville (
    source_type VARCHAR(16) NOT NULL
        CHECK (source_type IN ('node', 'way', 'relation')),
    source_id VARCHAR(32) NOT NULL,
    nom VARCHAR(200) NOT NULL CHECK (btrim(nom) <> ''),
    PRIMARY KEY (source_type, source_id)
);

CREATE INDEX IF NOT EXISTS ix_ville_nom
    ON ville (lower(nom), source_type, source_id);

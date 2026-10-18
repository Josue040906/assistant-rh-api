ALTER TABLE ville
    DROP CONSTRAINT IF EXISTS ville_source_type_check;

ALTER TABLE ville
    DROP CONSTRAINT IF EXISTS ck_ville_source_type;

ALTER TABLE ville
    ADD CONSTRAINT ck_ville_source_type
    CHECK (source_type IN ('node', 'way', 'relation', 'geonames'));

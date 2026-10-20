CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_employe_nom_trgm
    ON employe USING GIN (
        translate(
            lower(coalesce(nom, '')),
            U&'\00e0\00e1\00e2\00e3\00e4\00e5\00e8\00e9\00ea\00eb\00ec\00ed\00ee\00ef\00f2\00f3\00f4\00f5\00f6\00f9\00fa\00fb\00fc\00e7\00f1\00fd\00ff',
            'aaaaaaeeeeiiiiooooouuuucnyy'
        ) gin_trgm_ops
    );

CREATE INDEX IF NOT EXISTS idx_employe_prenom_trgm
    ON employe USING GIN (
        translate(
            lower(coalesce(prenom, '')),
            U&'\00e0\00e1\00e2\00e3\00e4\00e5\00e8\00e9\00ea\00eb\00ec\00ed\00ee\00ef\00f2\00f3\00f4\00f5\00f6\00f9\00fa\00fb\00fc\00e7\00f1\00fd\00ff',
            'aaaaaaeeeeiiiiooooouuuucnyy'
        ) gin_trgm_ops
    );

CREATE INDEX IF NOT EXISTS idx_poste_intitule_trgm
    ON poste USING GIN (
        translate(
            lower(coalesce(intitule, '')),
            U&'\00e0\00e1\00e2\00e3\00e4\00e5\00e8\00e9\00ea\00eb\00ec\00ed\00ee\00ef\00f2\00f3\00f4\00f5\00f6\00f9\00fa\00fb\00fc\00e7\00f1\00fd\00ff',
            'aaaaaaeeeeiiiiooooouuuucnyy'
        ) gin_trgm_ops
    );

CREATE INDEX IF NOT EXISTS idx_service_nom_trgm
    ON service USING GIN (
        translate(
            lower(coalesce(nom, '')),
            U&'\00e0\00e1\00e2\00e3\00e4\00e5\00e8\00e9\00ea\00eb\00ec\00ed\00ee\00ef\00f2\00f3\00f4\00f5\00f6\00f9\00fa\00fb\00fc\00e7\00f1\00fd\00ff',
            'aaaaaaeeeeiiiiooooouuuucnyy'
        ) gin_trgm_ops
    );

CREATE INDEX IF NOT EXISTS idx_direction_nom_trgm
    ON direction USING GIN (
        translate(
            lower(coalesce(nom, '')),
            U&'\00e0\00e1\00e2\00e3\00e4\00e5\00e8\00e9\00ea\00eb\00ec\00ed\00ee\00ef\00f2\00f3\00f4\00f5\00f6\00f9\00fa\00fb\00fc\00e7\00f1\00fd\00ff',
            'aaaaaaeeeeiiiiooooouuuucnyy'
        ) gin_trgm_ops
    );

CREATE UNIQUE INDEX IF NOT EXISTS uq_affectation_employe_active
    ON affectation (employe_id)
    WHERE date_fin IS NULL;

BEGIN;

ALTER TABLE public.document
    ADD COLUMN IF NOT EXISTS destinataire character varying(255),
    ADD COLUMN IF NOT EXISTS date_creation timestamp with time zone
        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS donnees jsonb
        NOT NULL DEFAULT '{}'::jsonb;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_document_donnees_object'
          AND conrelid = 'public.document'::regclass
    ) THEN
        ALTER TABLE public.document
            ADD CONSTRAINT ck_document_donnees_object
            CHECK (jsonb_typeof(donnees) = 'object');
    END IF;
END
$$;

INSERT INTO public.type_document (code, libelle, description)
VALUES
    ('CONGE', U&'Demande de cong\00E9', U&'Demande de cong\00E9 administratif.'),
    ('AVANCEMENT', 'Demande d''avancement', 'Demande d''avancement professionnel.'),
    ('RETRAITE', 'Demande de retraite', U&'Demande relative au d\00E9part \00E0 la retraite.')
ON CONFLICT (code) DO NOTHING;

COMMIT;

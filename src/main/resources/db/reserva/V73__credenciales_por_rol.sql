BEGIN;

ALTER TABLE plantilla_credencial ADD COLUMN IF NOT EXISTS rol VARCHAR(20);
ALTER TABLE responsable ADD COLUMN IF NOT EXISTS sin_datos BOOLEAN NOT NULL DEFAULT FALSE;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'plantilla_credencial_rol_valido') THEN
        ALTER TABLE plantilla_credencial ADD CONSTRAINT plantilla_credencial_rol_valido
            CHECK (rol IS NULL OR rol IN ('ENTRADA', 'REPRESENTANTE', 'ACOMPANANTE'));
    END IF;
END $$;

UPDATE edicion e SET credencial_acompanantes = FALSE
 WHERE credencial_acompanantes AND NOT EXISTS (SELECT 1 FROM edicion_dia d WHERE d.id_edicion = e.id);

COMMIT;

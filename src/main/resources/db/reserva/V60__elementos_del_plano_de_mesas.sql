BEGIN;

ALTER TABLE mesa_evento ADD COLUMN IF NOT EXISTS plano_elementos text;

COMMIT;

BEGIN;

ALTER TABLE edicion ADD COLUMN IF NOT EXISTS tipo VARCHAR(40);

ALTER TABLE categoria ADD COLUMN IF NOT EXISTS id_edicion BIGINT;

UPDATE categoria
   SET id_edicion = (SELECT e.id FROM edicion e WHERE e.activa ORDER BY e.anio DESC, e.id DESC LIMIT 1)
 WHERE id_edicion IS NULL;

CREATE INDEX IF NOT EXISTS categoria_edicion ON categoria (id_edicion);

CREATE UNIQUE INDEX IF NOT EXISTS edicion_una_activa ON edicion ((activa)) WHERE activa;

COMMIT;

BEGIN;

ALTER TABLE edicion ADD COLUMN IF NOT EXISTS venta_desde TIMESTAMP;
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS estado_evento VARCHAR(12) NOT NULL DEFAULT 'NORMAL';
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS estado_motivo VARCHAR(500);
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS estado_nueva_fecha VARCHAR(160);
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS estado_publico BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS estado_cambiado_en TIMESTAMP;
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS qr_estilo VARCHAR(2000);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'edicion_estado_evento_valido') THEN
        ALTER TABLE edicion ADD CONSTRAINT edicion_estado_evento_valido
            CHECK (estado_evento IN ('NORMAL', 'POSPUESTA', 'CANCELADA'));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS actividad_respaldo (
    id                    BIGSERIAL PRIMARY KEY,
    id_edicion_original   BIGINT,
    nombre                VARCHAR(160) NOT NULL,
    datos                 TEXT NOT NULL,
    creado_en             TIMESTAMP NOT NULL DEFAULT now(),
    creado_por            BIGINT,
    vence_en              TIMESTAMP NOT NULL,
    restaurado_en         TIMESTAMP,
    id_edicion_restaurada BIGINT
);

CREATE INDEX IF NOT EXISTS ix_actividad_respaldo_vigente ON actividad_respaldo (vence_en) WHERE restaurado_en IS NULL;

COMMIT;

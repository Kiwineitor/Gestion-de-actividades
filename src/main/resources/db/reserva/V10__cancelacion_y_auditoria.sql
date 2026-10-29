ALTER TABLE inscripcion ADD COLUMN IF NOT EXISTS motivo_cancelacion text;
ALTER TABLE inscripcion ADD COLUMN IF NOT EXISTS fecha_cancelacion timestamp;
ALTER TABLE inscripcion ADD COLUMN IF NOT EXISTS cancelada_por_id_usuario bigint;
ALTER TABLE inscripcion ADD COLUMN IF NOT EXISTS origen_cancelacion varchar(10);

CREATE TABLE IF NOT EXISTS auditoria (
    id              bigserial PRIMARY KEY,
    tabla           varchar(60)  NOT NULL,
    id_registro     bigint       NOT NULL,
    accion          varchar(40)  NOT NULL,
    detalle         text,
    id_usuario      bigint,
    usuario_nombre  varchar(120),
    origen          varchar(10)  NOT NULL DEFAULT 'WEB',
    fecha           timestamp    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_auditoria_registro ON auditoria (tabla, id_registro, fecha);
CREATE INDEX IF NOT EXISTS ix_auditoria_fecha    ON auditoria (fecha);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'inscripcion' AND column_name = 'motivo_cancelacion') THEN
        RAISE EXCEPTION 'No se creo inscripcion.motivo_cancelacion';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                    WHERE table_name = 'auditoria') THEN
        RAISE EXCEPTION 'No se creo la tabla auditoria';
    END IF;
END $$;

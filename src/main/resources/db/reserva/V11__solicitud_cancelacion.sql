CREATE TABLE IF NOT EXISTS solicitud_cancelacion (
    id                      bigserial   PRIMARY KEY,
    id_inscripcion          bigint      NOT NULL,
    motivo                  text        NOT NULL,
    respuesta               text,
    estado_solicitud        varchar(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha_solicitud         timestamp   NOT NULL DEFAULT now(),
    fecha_resolucion        timestamp,
    _registro_id_usuario    bigint,
    _modificacion_id_usuario bigint,
    _fecha_registro         timestamp NOT NULL DEFAULT now(),
    _fecha_modificacion     timestamp NOT NULL DEFAULT now(),
    _estado                 varchar(10) NOT NULL DEFAULT 'ACTIVO'
);

CREATE INDEX IF NOT EXISTS ix_solicitud_cancelacion_inscripcion
    ON solicitud_cancelacion (id_inscripcion);
CREATE INDEX IF NOT EXISTS ix_solicitud_cancelacion_estado
    ON solicitud_cancelacion (_estado);

ALTER TABLE solicitud_cancelacion ADD COLUMN IF NOT EXISTS _fecha_registro timestamp NOT NULL DEFAULT now();
ALTER TABLE solicitud_cancelacion ADD COLUMN IF NOT EXISTS _fecha_modificacion timestamp NOT NULL DEFAULT now();

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                    WHERE table_name = 'solicitud_cancelacion') THEN
        RAISE EXCEPTION 'No se creo la tabla solicitud_cancelacion';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'solicitud_cancelacion' AND column_name = 'estado_solicitud') THEN
        RAISE EXCEPTION 'No se creo solicitud_cancelacion.estado_solicitud';
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS puesto_foto (
    id                        bigserial PRIMARY KEY,
    id_puesto                 bigint NOT NULL REFERENCES puesto (id),
    ruta                      varchar(300) NOT NULL,
    descripcion               varchar(200),
    orden                     integer NOT NULL DEFAULT 0,
    "_estado"                varchar(20) DEFAULT 'A',
    "_fecha_registro"        timestamp,
    "_registro_id_usuario"   bigint,
    "_fecha_modificacion"    timestamp,
    "_modificacion_id_usuario" bigint
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'puesto_foto' AND column_name = '_registro_idUsuario') THEN
        ALTER TABLE puesto_foto RENAME COLUMN "_registro_idUsuario" TO "_registro_id_usuario";
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
                WHERE table_name = 'puesto_foto' AND column_name = '_modificacion_idUsuario') THEN
        ALTER TABLE puesto_foto RENAME COLUMN "_modificacion_idUsuario" TO "_modificacion_id_usuario";
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_puesto_foto_puesto ON puesto_foto (id_puesto, orden);

ALTER TABLE puesto ADD COLUMN IF NOT EXISTS referencia varchar(200);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'puesto' AND column_name = 'referencia') THEN
        RAISE EXCEPTION 'No se creo puesto.referencia';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.tables
                    WHERE table_name = 'puesto_foto') THEN
        RAISE EXCEPTION 'No se creo la tabla puesto_foto';
    END IF;
END $$;

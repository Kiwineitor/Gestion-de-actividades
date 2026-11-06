BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'edicion' AND column_name = 'configuracion_paso') THEN
        ALTER TABLE edicion ADD COLUMN configuracion_paso INTEGER NOT NULL DEFAULT 1;
        ALTER TABLE edicion ADD COLUMN usa_credenciales BOOLEAN NOT NULL DEFAULT FALSE;
        ALTER TABLE edicion ADD COLUMN credencial_representante BOOLEAN NOT NULL DEFAULT FALSE;
        ALTER TABLE edicion ADD COLUMN credencial_acompanantes BOOLEAN NOT NULL DEFAULT FALSE;
        UPDATE edicion SET configuracion_paso = 99, usa_credenciales = TRUE,
                           credencial_representante = TRUE, credencial_acompanantes = TRUE;
    END IF;
END $$;

ALTER TABLE edicion
    ADD COLUMN IF NOT EXISTS tipo_espacio VARCHAR(20) NOT NULL DEFAULT 'PUESTO',
    ADD COLUMN IF NOT EXISTS etiqueta_espacio VARCHAR(40),
    ADD COLUMN IF NOT EXISTS comprador VARCHAR(10) NOT NULL DEFAULT 'NEGOCIO',
    ADD COLUMN IF NOT EXISTS vende_espacios BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS vende_entradas BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS condiciones TEXT,
    ADD COLUMN IF NOT EXISTS minutos_bloqueo INTEGER NOT NULL DEFAULT 10,
    ADD COLUMN IF NOT EXISTS venta_hasta TIMESTAMP,
    ADD COLUMN IF NOT EXISTS meta_recaudacion NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS finalizada BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS credencial_entrada BOOLEAN NOT NULL DEFAULT FALSE;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'edicion_tipo_espacio_valido') THEN
        ALTER TABLE edicion ADD CONSTRAINT edicion_tipo_espacio_valido
            CHECK (tipo_espacio IN ('MESA', 'PUESTO', 'STAND', 'CASETA', 'CARPA', 'OTRO'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'edicion_comprador_valido') THEN
        ALTER TABLE edicion ADD CONSTRAINT edicion_comprador_valido CHECK (comprador IN ('PERSONA', 'NEGOCIO'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'edicion_minutos_bloqueo_valido') THEN
        ALTER TABLE edicion ADD CONSTRAINT edicion_minutos_bloqueo_valido CHECK (minutos_bloqueo BETWEEN 5 AND 120);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS edicion_dia (
    id           BIGSERIAL PRIMARY KEY,
    id_edicion   BIGINT NOT NULL REFERENCES edicion (id) ON DELETE CASCADE,
    fecha        DATE   NOT NULL,
    hora_inicio  TIME,
    hora_fin     TIME,
    UNIQUE (id_edicion, fecha)
);

CREATE TABLE IF NOT EXISTS entrada_tipo (
    id                     BIGSERIAL PRIMARY KEY,
    id_edicion             BIGINT        NOT NULL REFERENCES edicion (id) ON DELETE CASCADE,
    nombre                 VARCHAR(80)   NOT NULL,
    descripcion            VARCHAR(300),
    precio                 NUMERIC(10,2) NOT NULL DEFAULT 0,
    cupo                   INTEGER,
    orden                  INTEGER       NOT NULL DEFAULT 0,
    "_estado"              VARCHAR(1)    NOT NULL DEFAULT 'A',
    "_fecha_registro"      TIMESTAMP     NOT NULL DEFAULT now(),
    "_registro_id_usuario" BIGINT,
    CONSTRAINT entrada_tipo_precio_valido CHECK (precio >= 0),
    CONSTRAINT entrada_tipo_cupo_valido CHECK (cupo IS NULL OR cupo > 0)
);

CREATE INDEX IF NOT EXISTS entrada_tipo_edicion_idx ON entrada_tipo (id_edicion) WHERE "_estado" <> 'X';

COMMIT;

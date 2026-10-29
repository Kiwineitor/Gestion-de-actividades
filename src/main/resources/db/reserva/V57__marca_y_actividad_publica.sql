BEGIN;

CREATE TABLE IF NOT EXISTS parametro_sistema (
    clave           VARCHAR(60) PRIMARY KEY,
    valor           TEXT,
    actualizado_en  TIMESTAMP NOT NULL DEFAULT now(),
    actualizado_por BIGINT
);

INSERT INTO parametro_sistema (clave, valor) VALUES
    ('nombre', 'Gestión de Actividades'),
    ('lema', 'Eventos, ferias y reservas en un solo lugar'),
    ('contacto', NULL),
    ('logo_archivo', NULL)
ON CONFLICT (clave) DO NOTHING;

ALTER TABLE edicion ADD COLUMN IF NOT EXISTS lugar VARCHAR(200);
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS fechas_texto VARCHAR(120);
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS descripcion_publica TEXT;
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS contacto VARCHAR(120);
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS portada_archivo VARCHAR(300);
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS publica BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS recibe_interesados BOOLEAN NOT NULL DEFAULT TRUE;

COMMIT;

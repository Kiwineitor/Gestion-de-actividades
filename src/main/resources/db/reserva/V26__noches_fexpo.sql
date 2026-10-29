CREATE TABLE IF NOT EXISTS noche_fexpo (
    id                       BIGSERIAL PRIMARY KEY,
    id_edicion               BIGINT NOT NULL REFERENCES edicion(id),
    orden                    INTEGER NOT NULL DEFAULT 0,
    fecha                    DATE NOT NULL,
    titulo                   VARCHAR(120) NOT NULL,
    descripcion              VARCHAR(600),
    nombre_artista           VARCHAR(150),
    color                    VARCHAR(20),
    medio_tipo               VARCHAR(10),
    medio_archivo            VARCHAR(300),
    _fecha_registro          TIMESTAMP(6),
    _registro_id_usuario     BIGINT,
    _fecha_modificacion      TIMESTAMP(6),
    _modificacion_id_usuario BIGINT,
    _estado                  VARCHAR(1) NOT NULL DEFAULT 'A'
);

CREATE INDEX IF NOT EXISTS idx_noche_fexpo_edicion ON noche_fexpo (id_edicion);

COMMENT ON COLUMN noche_fexpo.medio_tipo    IS 'FOTO o VIDEO, segun la extension subida. NULL = sin medio, se ve la silueta de "por revelar".';
COMMENT ON COLUMN noche_fexpo.medio_archivo IS 'Ruta relativa bajo app.upload-root (bucket "noches"), servida en /files/**. NULL = sin medio.';
COMMENT ON COLUMN noche_fexpo.nombre_artista IS 'NULL o vacio = "Artista por revelar" (silueta animada) en la vista publica.';
COMMENT ON COLUMN noche_fexpo._estado IS 'A = activa, X = eliminada logicamente (no aparece en el panel ni en la vista publica).';

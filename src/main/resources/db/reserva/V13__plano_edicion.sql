ALTER TABLE edicion ADD COLUMN IF NOT EXISTS plano_archivo   VARCHAR(300);
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS plano_ancho     INTEGER;
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS plano_alto      INTEGER;
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS plano_version   INTEGER NOT NULL DEFAULT 0;
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS plano_subido_en TIMESTAMP;

COMMENT ON COLUMN edicion.plano_archivo IS 'Ruta relativa bajo app.upload-root, servida en /files/**. NULL = se usa el plano empaquetado de respaldo';
COMMENT ON COLUMN edicion.plano_ancho   IS 'Ancho en pixeles de la imagen: el visor deriva de aqui la proporcion del encuadre';
COMMENT ON COLUMN edicion.plano_alto    IS 'Alto en pixeles de la imagen';
COMMENT ON COLUMN edicion.plano_version IS 'Sube en cada reemplazo; viaja en la URL como ?v= para invalidar la cache del APK';

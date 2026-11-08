ALTER TABLE usuario ADD COLUMN IF NOT EXISTS codigo_recuperacion VARCHAR(100);
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS codigo_recuperacion_en TIMESTAMP;

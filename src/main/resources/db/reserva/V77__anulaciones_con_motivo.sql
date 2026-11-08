BEGIN;

ALTER TABLE interesado_stand ADD COLUMN IF NOT EXISTS motivo_descarte VARCHAR(500);
ALTER TABLE interesado_stand ADD COLUMN IF NOT EXISTS descartado_por BIGINT;
ALTER TABLE interesado_stand ADD COLUMN IF NOT EXISTS descartado_en TIMESTAMP;
ALTER TABLE interesado_stand ADD COLUMN IF NOT EXISTS comprobante_cambiado_por BIGINT;
ALTER TABLE interesado_stand ADD COLUMN IF NOT EXISTS comprobante_cambiado_en TIMESTAMP;

COMMIT;

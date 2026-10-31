BEGIN;

ALTER TABLE plano_texto ADD COLUMN IF NOT EXISTS fondo varchar(20);
ALTER TABLE plano_texto ADD COLUMN IF NOT EXISTS ancho double precision;
ALTER TABLE plano_texto ADD COLUMN IF NOT EXISTS alto double precision;

ALTER TABLE edicion ADD COLUMN IF NOT EXISTS plano_sin_fondo boolean NOT NULL DEFAULT false;
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS pago_qr_archivo varchar(300);
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS pago_banco varchar(80);
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS pago_cuenta varchar(60);
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS pago_titular varchar(120);
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS pago_instrucciones varchar(500);
ALTER TABLE edicion ADD COLUMN IF NOT EXISTS pago_oficina varchar(300);

ALTER TABLE interesado_stand ADD COLUMN IF NOT EXISTS id_puesto bigint REFERENCES puesto(id);
ALTER TABLE interesado_stand ADD COLUMN IF NOT EXISTS modo_pago varchar(20);
ALTER TABLE interesado_stand ADD COLUMN IF NOT EXISTS comprobante_archivo varchar(300);

CREATE UNIQUE INDEX IF NOT EXISTS ux_interesado_stand_puesto_pendiente
    ON interesado_stand (id_puesto)
 WHERE id_puesto IS NOT NULL AND estado = 'NUEVO';

COMMIT;

BEGIN;

ALTER TABLE mesa_reserva
    ADD COLUMN IF NOT EXISTS id_mesa_adicional BIGINT REFERENCES mesa (id),
    ADD COLUMN IF NOT EXISTS monto_mesa_adicional NUMERIC(10,2),
    ADD COLUMN IF NOT EXISTS manillas_mesa_adicional INTEGER,
    ADD COLUMN IF NOT EXISTS retiro_fisico BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE mesa_evento ALTER COLUMN minutos_bloqueo SET DEFAULT 10;
UPDATE mesa_evento SET minutos_bloqueo = 10 WHERE minutos_bloqueo NOT IN (5, 10);

COMMIT;

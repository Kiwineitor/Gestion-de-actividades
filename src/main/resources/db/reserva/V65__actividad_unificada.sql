BEGIN;

ALTER TABLE plantilla_credencial ADD COLUMN IF NOT EXISTS id_evento BIGINT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'plantilla_credencial_id_evento_fkey') THEN
        ALTER TABLE plantilla_credencial
            ADD CONSTRAINT plantilla_credencial_id_evento_fkey FOREIGN KEY (id_evento) REFERENCES mesa_evento(id);
    END IF;
END $$;

ALTER TABLE mesa_reserva ADD COLUMN IF NOT EXISTS registrado_por BIGINT;

CREATE INDEX IF NOT EXISTS ix_mesa_reserva_validado_por ON mesa_reserva (validado_por);
CREATE INDEX IF NOT EXISTS ix_mesa_reserva_registrado_por ON mesa_reserva (registrado_por);

COMMIT;

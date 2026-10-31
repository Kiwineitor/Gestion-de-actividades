BEGIN;

DROP INDEX IF EXISTS ux_interesado_stand_puesto_pendiente;

CREATE UNIQUE INDEX IF NOT EXISTS ux_interesado_stand_puesto_vigente
    ON interesado_stand (id_puesto)
 WHERE id_puesto IS NOT NULL AND estado IN ('NUEVO', 'ATENDIDO');

COMMIT;

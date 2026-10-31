BEGIN;

ALTER TABLE categoria ADD COLUMN IF NOT EXISTS personas_por_puesto INTEGER NOT NULL DEFAULT 2;
ALTER TABLE categoria ADD COLUMN IF NOT EXISTS costo_persona_extra NUMERIC(10,2) NOT NULL DEFAULT 15;

ALTER TABLE interesado_stand ADD COLUMN IF NOT EXISTS ci VARCHAR(20);
ALTER TABLE interesado_stand ADD COLUMN IF NOT EXISTS id_inscripcion BIGINT REFERENCES inscripcion(id);
CREATE INDEX IF NOT EXISTS ix_interesado_stand_inscripcion ON interesado_stand (id_inscripcion);

DROP INDEX IF EXISTS ux_interesado_stand_puesto_vigente;
CREATE UNIQUE INDEX IF NOT EXISTS ux_interesado_stand_puesto_vigente
    ON interesado_stand (id_puesto)
 WHERE id_puesto IS NOT NULL
   AND (estado = 'NUEVO' OR (estado = 'ATENDIDO' AND id_inscripcion IS NULL));

COMMIT;

BEGIN;

ALTER TABLE puesto
    ADD COLUMN IF NOT EXISTS reserva_token VARCHAR(40),
    ADD COLUMN IF NOT EXISTS reserva_origen VARCHAR(60);

ALTER TABLE interesado_stand ADD COLUMN IF NOT EXISTS reserva_token VARCHAR(40);

CREATE INDEX IF NOT EXISTS idx_puesto_reserva_token ON puesto (reserva_token) WHERE reserva_token IS NOT NULL;

UPDATE interesado_stand i
   SET reserva_token = 'sol-' || i.id
 WHERE i.reserva_token IS NULL AND i.id_puesto IS NOT NULL
   AND (i.estado = 'NUEVO' OR (i.estado = 'ATENDIDO' AND i.id_inscripcion IS NULL));

UPDATE puesto p
   SET estado_puesto = 'T', reservado_por_id_usuario = NULL, reserva_expira = NULL,
       reserva_token = i.reserva_token, version = p.version + 1
  FROM interesado_stand i
 WHERE i.id_puesto = p.id AND p.estado_puesto = 'L' AND p.reserva_token IS NULL
   AND (i.estado = 'NUEVO' OR (i.estado = 'ATENDIDO' AND i.id_inscripcion IS NULL));

INSERT INTO tipo_entidad (nombre, "_estado", "_fecha_registro")
SELECT 'PERSONA PARTICULAR', 'ACTIVO', now()
 WHERE NOT EXISTS (SELECT 1 FROM tipo_entidad WHERE upper(nombre) = 'PERSONA PARTICULAR');

COMMIT;

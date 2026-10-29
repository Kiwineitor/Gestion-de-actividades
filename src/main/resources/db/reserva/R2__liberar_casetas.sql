BEGIN;

DELETE FROM notificacion WHERE id_inscripcion IS NOT NULL OR id_puesto IS NOT NULL;

DELETE FROM inscripcion_puesto;

DELETE FROM solicitud_cancelacion;

DELETE FROM inscripcion;

DELETE FROM responsable;

DELETE FROM persona p
 WHERE p._estado IN ('RESPONSABLE', 'PROMOTOR')
   AND NOT EXISTS (SELECT 1 FROM usuario u WHERE u.persona_id = p.id)
   AND NOT EXISTS (SELECT 1 FROM responsable r WHERE r.id_persona = p.id);

DELETE FROM entidad;

UPDATE puesto
   SET estado_puesto = 'L',
       reservado_por_id_usuario = NULL,
       reserva_expira = NULL,
       version = COALESCE(version, 0) + 1
 WHERE _estado <> 'X'
   AND (estado_puesto <> 'L'
        OR reservado_por_id_usuario IS NOT NULL
        OR reserva_expira IS NOT NULL);

COMMIT;

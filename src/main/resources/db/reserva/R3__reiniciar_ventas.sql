BEGIN;

TRUNCATE
    inscripcion_puesto,
    notificacion,
    inscripcion,
    responsable,
    entidad,
    venta_boleto,
    solicitud_cancelacion,
    auditoria,
    control_responsable,
    control_acceso_responsable
  RESTART IDENTITY;

DELETE FROM persona p
 WHERE p._estado IN ('RESPONSABLE', 'PROMOTOR')
   AND NOT EXISTS (SELECT 1 FROM usuario u      WHERE u.persona_id = p.id)
   AND NOT EXISTS (SELECT 1 FROM admistrativo a WHERE a.id_persona = p.id)
   AND NOT EXISTS (SELECT 1 FROM responsable r  WHERE r.id_persona = p.id);

UPDATE puesto
   SET estado_puesto = 'L',
       reservado_por_id_usuario = NULL,
       reserva_expira = NULL,
       version = COALESCE(version, 0) + 1
 WHERE _estado <> 'X'
   AND (estado_puesto <> 'L'
        OR reservado_por_id_usuario IS NOT NULL
        OR reserva_expira IS NOT NULL);

DO $$
DECLARE
    t record;
    ultimo bigint;
BEGIN
    FOR t IN
        SELECT c.relname AS secuencia, tb.relname AS tabla, a.attname AS columna
          FROM pg_class c
          JOIN pg_depend d  ON d.objid = c.oid
          JOIN pg_class tb  ON tb.oid = d.refobjid
          JOIN pg_attribute a ON a.attrelid = tb.oid AND a.attnum = d.refobjsubid
         WHERE c.relkind = 'S'
           AND tb.relname NOT IN ('inscripcion_puesto','notificacion','inscripcion','responsable',
                                  'entidad','venta_boleto','solicitud_cancelacion','auditoria',
                                  'control_responsable','control_acceso_responsable')
    LOOP
        EXECUTE format('SELECT COALESCE(max(%I), 0) FROM %I', t.columna, t.tabla) INTO ultimo;
        EXECUTE format('SELECT setval(%L, GREATEST(%s, 1), %L::boolean)',
                       t.secuencia, ultimo, ultimo > 0);
    END LOOP;
END $$;

COMMIT;

\echo ''
\echo '=== Estado tras el reinicio ==='
SELECT 'ventas'            AS que, count(*) AS filas FROM inscripcion
UNION ALL SELECT 'entidades',        count(*) FROM entidad
UNION ALL SELECT 'responsables',     count(*) FROM responsable
UNION ALL SELECT 'ventas boleteria', count(*) FROM venta_boleto
UNION ALL SELECT 'notificaciones',   count(*) FROM notificacion
UNION ALL SELECT '— se conserva —',  NULL
UNION ALL SELECT 'categorias',       count(*) FROM categoria WHERE _estado <> 'X'
UNION ALL SELECT 'casetas vivas',    count(*) FROM puesto    WHERE _estado <> 'X'
UNION ALL SELECT 'casetas libres',   count(*) FROM puesto    WHERE _estado <> 'X' AND estado_puesto = 'L'
UNION ALL SELECT 'casetas en plano', count(*) FROM puesto    WHERE mapa_x IS NOT NULL
UNION ALL SELECT 'usuarios',         count(*) FROM usuario   WHERE _estado <> 'ELIMINADO'
UNION ALL SELECT 'habilitaciones',   count(*) FROM vendedor_puesto
UNION ALL SELECT 'personas sistema', count(*) FROM persona   WHERE _estado = 'ACTIVO';
\echo ''
\echo 'La proxima venta sera la N.º 1.'

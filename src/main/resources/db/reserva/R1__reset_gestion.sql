BEGIN;

TRUNCATE inscripcion_puesto, inscripcion, venta_boleto RESTART IDENTITY CASCADE;

TRUNCATE control_responsable, control_acceso_responsable RESTART IDENTITY CASCADE;

TRUNCATE responsable, entidad RESTART IDENTITY CASCADE;

TRUNCATE puesto, categoria RESTART IDENTITY CASCADE;

TRUNCATE admistrativo RESTART IDENTITY CASCADE;

DELETE FROM usuario WHERE username NOT IN ('admin1', 'admin2');

DELETE FROM persona
 WHERE id NOT IN (SELECT persona_id FROM usuario WHERE persona_id IS NOT NULL);

DO $$
DECLARE
    sobrantes bigint;
BEGIN
    SELECT count(*) INTO sobrantes FROM inscripcion;
    IF sobrantes > 0 THEN
        RAISE EXCEPTION 'Quedaron % inscripciones: reset abortado', sobrantes;
    END IF;

    SELECT count(*) INTO sobrantes FROM usuario u
     WHERE NOT EXISTS (SELECT 1 FROM persona p WHERE p.id = u.persona_id);
    IF sobrantes > 0 THEN
        RAISE EXCEPTION 'Hay % usuarios sin persona: reset abortado', sobrantes;
    END IF;
END $$;

COMMIT;

SELECT 'rol' AS tabla, count(*) AS filas FROM rol
UNION ALL SELECT 'edicion', count(*) FROM edicion
UNION ALL SELECT 'usuario', count(*) FROM usuario
UNION ALL SELECT 'persona', count(*) FROM persona
UNION ALL SELECT 'categoria', count(*) FROM categoria
UNION ALL SELECT 'puesto', count(*) FROM puesto
UNION ALL SELECT 'entidad', count(*) FROM entidad
UNION ALL SELECT 'responsable', count(*) FROM responsable
UNION ALL SELECT 'inscripcion', count(*) FROM inscripcion
UNION ALL SELECT 'inscripcion_puesto', count(*) FROM inscripcion_puesto
ORDER BY tabla;

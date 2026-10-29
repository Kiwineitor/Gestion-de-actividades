ALTER TABLE responsable ADD COLUMN IF NOT EXISTS es_titular boolean NOT NULL DEFAULT false;

WITH primeros AS (
    SELECT DISTINCT ON (r.id_entidad) r.id
      FROM responsable r
     WHERE r."_estado" IS NULL OR r."_estado" <> 'X'
     ORDER BY r.id_entidad, r.id
)
UPDATE responsable r
   SET es_titular = true
  FROM primeros p
 WHERE r.id = p.id
   AND NOT EXISTS (
       SELECT 1 FROM responsable r2
        WHERE r2.id_entidad = r.id_entidad AND r2.es_titular
   );

UPDATE inscripcion
   SET id_edicion = (SELECT id FROM edicion WHERE activa LIMIT 1)
 WHERE id_edicion IS NULL
   AND EXISTS (SELECT 1 FROM edicion WHERE activa);

DO $$
DECLARE
    n bigint;
BEGIN
    SELECT count(*) INTO n
      FROM (SELECT r.id_entidad
              FROM responsable r
             WHERE (r."_estado" IS NULL OR r."_estado" <> 'X')
             GROUP BY r.id_entidad
            HAVING bool_or(r.es_titular) = false) x;
    IF n > 0 THEN
        RAISE EXCEPTION 'Quedan % entidades sin responsable titular', n;
    END IF;
END $$;

BEGIN;

UPDATE rol
   SET nombre = upper(btrim(regexp_replace(nombre, '\s{2,}', ' ', 'g')))
 WHERE nombre IS NOT NULL
   AND nombre <> upper(btrim(regexp_replace(nombre, '\s{2,}', ' ', 'g')));

WITH canonico AS (
    SELECT nombre, min(id) AS id_bueno
      FROM rol
     WHERE nombre IS NOT NULL
     GROUP BY nombre
    HAVING count(*) > 1
)
UPDATE usuario u
   SET rol_id = c.id_bueno
  FROM rol r
  JOIN canonico c ON c.nombre = r.nombre
 WHERE u.rol_id = r.id
   AND u.rol_id <> c.id_bueno;

DELETE FROM rol r
 WHERE EXISTS (
        SELECT 1 FROM rol otro
         WHERE otro.nombre = r.nombre
           AND otro.id < r.id)
   AND NOT EXISTS (SELECT 1 FROM usuario u WHERE u.rol_id = r.id);

INSERT INTO rol (nombre, descripcion, _estado, _fecha_registro, _fecha_modificacion)
SELECT v.nombre, v.descripcion, 'ACTIVO', now(), now()
  FROM (VALUES
        ('SUPER USUARIO',  'Control total: gestiona usuarios, roles, el plano de la feria y toda la administracion.'),
        ('ADMINISTRADOR',  'Administracion de la feria: usuarios, plano, inscripciones y reportes globales.'),
        ('ADMINISTRATIVO', 'Vendedor: reserva, vende y consulta sus propias ventas. No redisena el plano.'),
        ('CONTROL',        'Control de acceso en puerta: verifica credenciales y registra el ingreso de responsables.'),
        ('ASESORIA',       'Consulta: ve listados y reportes de la feria, sin modificar nada.')
       ) AS v(nombre, descripcion)
 WHERE NOT EXISTS (SELECT 1 FROM rol r WHERE r.nombre = v.nombre);

UPDATE rol r
   SET descripcion = v.descripcion,
       _fecha_modificacion = now()
  FROM (VALUES
        ('SUPER USUARIO',  'Control total: gestiona usuarios, roles, el plano de la feria y toda la administracion.'),
        ('ADMINISTRADOR',  'Administracion de la feria: usuarios, plano, inscripciones y reportes globales.'),
        ('ADMINISTRATIVO', 'Vendedor: reserva, vende y consulta sus propias ventas. No redisena el plano.'),
        ('CONTROL',        'Control de acceso en puerta: verifica credenciales y registra el ingreso de responsables.'),
        ('ASESORIA',       'Consulta: ve listados y reportes de la feria, sin modificar nada.')
       ) AS v(nombre, descripcion)
 WHERE r.nombre = v.nombre
   AND (r.descripcion IS NULL OR btrim(r.descripcion) = '');

UPDATE rol SET _estado = 'ACTIVO' WHERE _estado IS NULL OR btrim(_estado) = '';

CREATE UNIQUE INDEX IF NOT EXISTS ux_rol_nombre ON rol (nombre);

COMMIT;

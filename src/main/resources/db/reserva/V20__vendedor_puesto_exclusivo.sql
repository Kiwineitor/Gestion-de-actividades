BEGIN;

DELETE FROM vendedor_puesto vp
 WHERE EXISTS (
        SELECT 1 FROM vendedor_puesto otro
         WHERE otro.id_puesto = vp.id_puesto
           AND (otro._fecha_registro, otro.id) < (vp._fecha_registro, vp.id));

ALTER TABLE vendedor_puesto DROP CONSTRAINT IF EXISTS vendedor_puesto_id_usuario_id_puesto_key;

CREATE UNIQUE INDEX IF NOT EXISTS ux_vendedor_puesto_caseta ON vendedor_puesto (id_puesto);

DROP VIEW IF EXISTS v_puestos_por_vendedor;

CREATE VIEW v_puestos_por_vendedor AS
SELECT p.*, vc.id_usuario AS vendedor_id
  FROM puesto p
  INNER JOIN categoria c ON c.id = p.id_categoria AND c._estado <> 'X'
  INNER JOIN vendedor_categoria vc ON vc.id_categoria = c.id
 WHERE p._estado <> 'X'
   AND p.estado_puesto <> 'X'
   AND NOT EXISTS (SELECT 1 FROM vendedor_puesto vp WHERE vp.id_puesto = p.id)
UNION
SELECT p.*, vp.id_usuario AS vendedor_id
  FROM puesto p
  INNER JOIN categoria c ON c.id = p.id_categoria AND c._estado <> 'X'
  INNER JOIN vendedor_puesto vp ON vp.id_puesto = p.id
 WHERE p._estado <> 'X'
   AND p.estado_puesto <> 'X';

COMMENT ON VIEW v_puestos_por_vendedor IS
    'Casetas visibles para cada vendedor. Una caseta asignada individualmente es solo de su duenio y se recorta de la categoria.';

COMMIT;

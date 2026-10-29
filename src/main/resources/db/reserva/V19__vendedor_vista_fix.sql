BEGIN;

DROP VIEW IF EXISTS v_puestos_por_vendedor;

CREATE VIEW v_puestos_por_vendedor AS
SELECT p.*, vc.id_usuario AS vendedor_id
  FROM puesto p
  INNER JOIN categoria c ON c.id = p.id_categoria AND c._estado <> 'X'
  INNER JOIN vendedor_categoria vc ON vc.id_categoria = c.id
 WHERE p._estado <> 'X'
   AND p.estado_puesto <> 'X'
UNION
SELECT p.*, vp.id_usuario AS vendedor_id
  FROM puesto p
  INNER JOIN categoria c ON c.id = p.id_categoria AND c._estado <> 'X'
  INNER JOIN vendedor_puesto vp ON vp.id_puesto = p.id
 WHERE p._estado <> 'X'
   AND p.estado_puesto <> 'X';

COMMENT ON VIEW v_puestos_por_vendedor IS
    'Casetas visibles para cada vendedor: por categoria asignada o por caseta asignada. Una fila por (caseta, vendedor).';

COMMIT;

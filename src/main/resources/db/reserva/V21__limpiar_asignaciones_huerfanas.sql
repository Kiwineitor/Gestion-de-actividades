BEGIN;

DELETE FROM vendedor_puesto vp
 WHERE EXISTS (SELECT 1 FROM usuario u
                WHERE u.id = vp.id_usuario AND u._estado = 'ELIMINADO');

DELETE FROM vendedor_categoria vc
 WHERE EXISTS (SELECT 1 FROM usuario u
                WHERE u.id = vc.id_usuario AND u._estado = 'ELIMINADO');

COMMIT;

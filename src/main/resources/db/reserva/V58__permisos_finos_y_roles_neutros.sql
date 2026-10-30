BEGIN;

INSERT INTO rol_pantalla (id_rol, pantalla, _registro_id_usuario)
SELECT r.id, p.pantalla, NULL
  FROM rol r
 CROSS JOIN (VALUES ('actividades'), ('mesas-diseno'), ('credenciales-diseno')) AS p(pantalla)
 WHERE upper(btrim(r.nombre)) = 'ADMINISTRADOR'
   AND (r."_estado" IS NULL OR r."_estado" <> 'ELIMINADO')
   AND NOT EXISTS (
       SELECT 1 FROM rol_pantalla rp WHERE rp.id_rol = r.id AND rp.pantalla = p.pantalla
   );

UPDATE rol SET descripcion = d.texto
  FROM (VALUES
    ('SUPER USUARIO', 'Superadministrador: control total del sistema, la marca, los roles y todas las actividades.'),
    ('ADMINISTRADOR', 'Administrador: gestiona actividades, planos, precios, solicitudes, credenciales y usuarios.'),
    ('ADMINISTRATIVO', 'Operador de ventas: reserva, vende y consulta sus propias ventas.'),
    ('CONTROL', 'Control de acceso: verifica credenciales y registra ingresos.'),
    ('ASESORIA', 'Supervisor: consulta listados y reportes sin modificar nada.'),
    ('VERIFICADOR', 'Verificador: revisa que cada registro esté completo antes de imprimir credenciales.')
  ) AS d(nombre, texto)
 WHERE upper(btrim(rol.nombre)) = d.nombre;

COMMIT;

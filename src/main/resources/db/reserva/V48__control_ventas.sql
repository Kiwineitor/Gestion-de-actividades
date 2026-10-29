BEGIN;

INSERT INTO rol_pantalla (id_rol, pantalla, _registro_id_usuario)
SELECT r.id, 'control-ventas', NULL
  FROM rol r
 WHERE upper(btrim(r.nombre)) IN ('ADMINISTRADOR', 'ASESORIA')
   AND (r."_estado" IS NULL OR r."_estado" <> 'ELIMINADO')
   AND NOT EXISTS (
       SELECT 1 FROM rol_pantalla rp WHERE rp.id_rol = r.id AND rp.pantalla = 'control-ventas'
   );

COMMIT;

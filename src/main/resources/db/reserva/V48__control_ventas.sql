-- =============================================================================
-- V48 - Pantalla "Control de ventas" (rendicion de cuentas de la feria).
--
-- El modulo NO crea tablas: todo lo que muestra se deriva de lo que ya existe (ventas, casetas
-- con su precio congelado, responsables y extras, comprobantes adjuntos y credenciales
-- generadas). Rendir una venta es adjuntarle su comprobante, con el flujo que ya existia.
--
-- Solo da la pantalla a ADMINISTRADOR y ASESORIA. El servidor autoriza aparte
-- (Roles.VE_REPORTES); esto solo muestra el enlace del menu. SUPER USUARIO ve todo por codigo,
-- sin fila.
--
-- Idempotente: se puede ejecutar varias veces.
-- =============================================================================
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

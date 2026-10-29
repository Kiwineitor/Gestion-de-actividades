CREATE TABLE IF NOT EXISTS rol_pantalla (
    id                       BIGSERIAL PRIMARY KEY,
    id_rol                   BIGINT NOT NULL REFERENCES rol(id) ON DELETE CASCADE,
    pantalla                 VARCHAR(40) NOT NULL,
    _fecha_registro          TIMESTAMP NOT NULL DEFAULT now(),
    _registro_id_usuario     BIGINT,
    UNIQUE (id_rol, pantalla)
);

COMMENT ON TABLE rol_pantalla IS 'Pantallas visibles para cada rol. Controla el menu y el acceso a las rutas de la SPA, NO la autorizacion del servidor.';

INSERT INTO rol_pantalla (id_rol, pantalla, _registro_id_usuario)
SELECT r.id, 'puestos', NULL
  FROM rol r
 WHERE upper(btrim(r.nombre)) IN ('ADMINISTRADOR')
   AND (r."_estado" IS NULL OR r."_estado" <> 'ELIMINADO')
   AND NOT EXISTS (
       SELECT 1
         FROM rol_pantalla rp
        WHERE rp.id_rol = r.id
          AND rp.pantalla = 'puestos'
   );

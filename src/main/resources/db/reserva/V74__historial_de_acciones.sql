BEGIN;

CREATE TABLE IF NOT EXISTS historial_accion (
    id           BIGSERIAL PRIMARY KEY,
    cuando       TIMESTAMP    NOT NULL DEFAULT now(),
    id_usuario   BIGINT,
    usuario      VARCHAR(160),
    rol          VARCHAR(80),
    modulo       VARCHAR(60)  NOT NULL,
    accion       VARCHAR(300) NOT NULL,
    metodo       VARCHAR(10),
    ruta         VARCHAR(300),
    id_registro  BIGINT,
    estado_http  INTEGER,
    ok           BOOLEAN      NOT NULL DEFAULT TRUE,
    ip           VARCHAR(60),
    detalle      TEXT
);

CREATE INDEX IF NOT EXISTS historial_accion_cuando_idx ON historial_accion (cuando DESC);
CREATE INDEX IF NOT EXISTS historial_accion_usuario_idx ON historial_accion (id_usuario, cuando DESC);
CREATE INDEX IF NOT EXISTS historial_accion_modulo_idx ON historial_accion (modulo, cuando DESC);

INSERT INTO rol_pantalla (id_rol, pantalla)
SELECT r.id, 'historial'
  FROM rol r
 WHERE upper(btrim(r.nombre)) IN ('SUPER USUARIO', 'ADMINISTRADOR')
   AND NOT EXISTS (SELECT 1 FROM rol_pantalla rp WHERE rp.id_rol = r.id AND rp.pantalla = 'historial');

COMMIT;

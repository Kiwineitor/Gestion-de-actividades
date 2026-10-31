BEGIN;

ALTER TABLE mesa_evento ADD COLUMN IF NOT EXISTS tipos_pase text;

ALTER TABLE mesa_reserva ADD COLUMN IF NOT EXISTS personas integer;
ALTER TABLE mesa_reserva ADD COLUMN IF NOT EXISTS tipo_pase varchar(60);

CREATE TABLE IF NOT EXISTS mesa_pase (
    id          bigserial PRIMARY KEY,
    id_reserva  bigint      NOT NULL REFERENCES mesa_reserva (id),
    numero      integer     NOT NULL,
    codigo      varchar(40) NOT NULL UNIQUE,
    tipo        varchar(60),
    creado_en   timestamp   NOT NULL DEFAULT now(),
    ingreso_en  timestamp,
    ingreso_por bigint,
    UNIQUE (id_reserva, numero)
);

CREATE INDEX IF NOT EXISTS idx_mesa_pase_reserva ON mesa_pase (id_reserva);

INSERT INTO rol_pantalla (id_rol, pantalla, _registro_id_usuario)
SELECT r.id, 'mesas-ingreso', NULL
  FROM rol r
 WHERE upper(btrim(r.nombre)) IN ('ADMINISTRADOR', 'CONTROL')
   AND (r."_estado" IS NULL OR r."_estado" <> 'ELIMINADO')
   AND NOT EXISTS (
       SELECT 1 FROM rol_pantalla rp WHERE rp.id_rol = r.id AND rp.pantalla = 'mesas-ingreso'
   );

COMMIT;

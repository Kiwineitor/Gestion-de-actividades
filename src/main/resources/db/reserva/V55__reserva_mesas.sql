BEGIN;

CREATE TABLE IF NOT EXISTS mesa_evento (
    id                         BIGSERIAL PRIMARY KEY,
    nombre                     VARCHAR(120) NOT NULL,
    local_nombre               VARCHAR(120) NOT NULL,
    local_direccion            VARCHAR(200),
    descripcion                VARCHAR(500),
    fecha_inicio               TIMESTAMP    NOT NULL,
    hora_limite                TIMESTAMP,
    minutos_bloqueo            INTEGER      NOT NULL DEFAULT 5,
    plano_archivo              VARCHAR(300),
    plano_ancho                INTEGER,
    plano_alto                 INTEGER,
    pago_qr_archivo            VARCHAR(300),
    pago_banco                 VARCHAR(80),
    pago_cuenta                VARCHAR(60),
    pago_titular               VARCHAR(120),
    pago_instrucciones         VARCHAR(500),
    celular_contacto           VARCHAR(30),
    politicas                  TEXT,
    publicado                  BOOLEAN      NOT NULL DEFAULT FALSE,
    "_estado"                  VARCHAR(1)   NOT NULL DEFAULT 'A',
    "_fecha_registro"          TIMESTAMP    NOT NULL DEFAULT now(),
    "_registro_id_usuario"     BIGINT,
    "_fecha_modificacion"      TIMESTAMP,
    "_modificacion_id_usuario" BIGINT
);

CREATE TABLE IF NOT EXISTS mesa (
    id                         BIGSERIAL PRIMARY KEY,
    id_evento                  BIGINT       NOT NULL REFERENCES mesa_evento (id),
    codigo                     VARCHAR(20)  NOT NULL,
    zona                       VARCHAR(60),
    capacidad                  INTEGER      NOT NULL DEFAULT 4,
    precio                     NUMERIC(10,2) NOT NULL DEFAULT 0,
    beneficios                 VARCHAR(300),
    forma                      VARCHAR(20)  NOT NULL DEFAULT 'circulo',
    color                      VARCHAR(9),
    mapa_x                     DOUBLE PRECISION,
    mapa_y                     DOUBLE PRECISION,
    tamano                     DOUBLE PRECISION NOT NULL DEFAULT 0.06,
    estado                     VARCHAR(1)   NOT NULL DEFAULT 'L',
    id_reserva                 BIGINT,
    bloqueo_expira             TIMESTAMP,
    version                    BIGINT       NOT NULL DEFAULT 0,
    "_estado"                  VARCHAR(1)   NOT NULL DEFAULT 'A',
    "_fecha_registro"          TIMESTAMP    NOT NULL DEFAULT now(),
    "_registro_id_usuario"     BIGINT,
    "_fecha_modificacion"      TIMESTAMP,
    "_modificacion_id_usuario" BIGINT,
    CONSTRAINT mesa_estado_valido CHECK (estado IN ('L', 'T', 'R', 'O', 'X'))
);

CREATE UNIQUE INDEX IF NOT EXISTS mesa_codigo_por_evento ON mesa (id_evento, codigo) WHERE "_estado" = 'A';
CREATE INDEX IF NOT EXISTS mesa_evento_idx ON mesa (id_evento);
CREATE INDEX IF NOT EXISTS mesa_bloqueo_idx ON mesa (bloqueo_expira) WHERE estado = 'T';

CREATE TABLE IF NOT EXISTS mesa_reserva (
    id                         BIGSERIAL PRIMARY KEY,
    id_evento                  BIGINT       NOT NULL REFERENCES mesa_evento (id),
    id_mesa                    BIGINT       NOT NULL REFERENCES mesa (id),
    codigo                     VARCHAR(40)  NOT NULL UNIQUE,
    nombre                     VARCHAR(80)  NOT NULL,
    apellidos                  VARCHAR(80)  NOT NULL,
    ci                         VARCHAR(20)  NOT NULL,
    celular                    VARCHAR(20)  NOT NULL,
    correo                     VARCHAR(120),
    monto                      NUMERIC(10,2) NOT NULL,
    estado                     VARCHAR(15)  NOT NULL,
    bloqueo_expira             TIMESTAMP,
    comprobante_archivo        VARCHAR(300),
    comprobante_subido_en      TIMESTAMP,
    validado_por               BIGINT,
    validado_en                TIMESTAMP,
    motivo                     VARCHAR(300),
    ingreso_en                 TIMESTAMP,
    ingreso_por                BIGINT,
    ip                         VARCHAR(60),
    creado_en                  TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT mesa_reserva_estado_valido CHECK (estado IN
        ('BLOQUEADA', 'EN_REVISION', 'CONFIRMADA', 'RECHAZADA', 'VENCIDA', 'CANCELADA'))
);

CREATE INDEX IF NOT EXISTS mesa_reserva_evento_estado ON mesa_reserva (id_evento, estado);
CREATE INDEX IF NOT EXISTS mesa_reserva_ci ON mesa_reserva (id_evento, ci);
CREATE INDEX IF NOT EXISTS mesa_reserva_celular ON mesa_reserva (id_evento, celular);

COMMIT;

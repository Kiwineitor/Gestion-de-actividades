BEGIN;

CREATE TABLE IF NOT EXISTS plantilla_credencial (
    id                         BIGSERIAL PRIMARY KEY,
    codigo                     VARCHAR(30)  NOT NULL UNIQUE,
    etiqueta                   VARCHAR(80)  NOT NULL,
    detalle                    VARCHAR(200),
    imagen                     VARCHAR(300) NOT NULL,
    requiere_foto              BOOLEAN      NOT NULL DEFAULT TRUE,
    centrado                   BOOLEAN      NOT NULL DEFAULT FALSE,
    mayusculas                 BOOLEAN      NOT NULL DEFAULT TRUE,
    foto_circular              BOOLEAN      NOT NULL DEFAULT FALSE,
    multilinea                 BOOLEAN      NOT NULL DEFAULT FALSE,
    color_texto                VARCHAR(9),
    tipo_texto                 VARCHAR(40),
    cajas                      TEXT         NOT NULL,
    orden                      INTEGER      NOT NULL DEFAULT 0,
    predeterminada             BOOLEAN      NOT NULL DEFAULT FALSE,
    "_estado"                  VARCHAR(10)  NOT NULL DEFAULT 'A',
    "_fecha_registro"          TIMESTAMP    NOT NULL DEFAULT now(),
    "_registro_id_usuario"     BIGINT,
    "_fecha_modificacion"      TIMESTAMP,
    "_modificacion_id_usuario" BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS plantilla_credencial_una_predeterminada
    ON plantilla_credencial ((predeterminada))
    WHERE predeterminada AND "_estado" = 'A';

ALTER TABLE responsable ADD COLUMN IF NOT EXISTS cargo VARCHAR(60);

CREATE TABLE IF NOT EXISTS credencial_cambio (
    id                     BIGSERIAL PRIMARY KEY,
    id_responsable         BIGINT       NOT NULL,
    id_persona             BIGINT,
    campo                  VARCHAR(30)  NOT NULL,
    valor_anterior         VARCHAR(300),
    valor_nuevo            VARCHAR(300),
    "_fecha_registro"      TIMESTAMP    NOT NULL DEFAULT now(),
    "_registro_id_usuario" BIGINT
);

CREATE INDEX IF NOT EXISTS credencial_cambio_responsable ON credencial_cambio (id_responsable);

COMMIT;

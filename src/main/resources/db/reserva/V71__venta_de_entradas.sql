BEGIN;

CREATE TABLE IF NOT EXISTS entrada_compra (
    id                  BIGSERIAL PRIMARY KEY,
    id_edicion          BIGINT        NOT NULL REFERENCES edicion (id),
    codigo              VARCHAR(20)   NOT NULL UNIQUE,
    nombre              VARCHAR(160)  NOT NULL,
    ci                  VARCHAR(20),
    celular             VARCHAR(30)   NOT NULL,
    total               NUMERIC(12,2) NOT NULL DEFAULT 0,
    estado              VARCHAR(12)   NOT NULL DEFAULT 'REVISION',
    modo_pago           VARCHAR(12),
    comprobante_archivo VARCHAR(300),
    motivo              VARCHAR(300),
    origen              VARCHAR(60),
    registrado_por      BIGINT,
    validado_por        BIGINT,
    validado_en         TIMESTAMP,
    creado_en           TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT entrada_compra_estado_valido CHECK (estado IN ('REVISION', 'APROBADA', 'RECHAZADA', 'ANULADA')),
    CONSTRAINT entrada_compra_modo_valido CHECK (modo_pago IS NULL OR modo_pago IN ('COMPROBANTE', 'OFICINA', 'EFECTIVO', 'QR', 'GRATIS'))
);

CREATE INDEX IF NOT EXISTS entrada_compra_edicion_estado_idx ON entrada_compra (id_edicion, estado);
CREATE INDEX IF NOT EXISTS entrada_compra_celular_idx ON entrada_compra (celular);

CREATE TABLE IF NOT EXISTS entrada_compra_detalle (
    id        BIGSERIAL PRIMARY KEY,
    id_compra BIGINT        NOT NULL REFERENCES entrada_compra (id) ON DELETE CASCADE,
    id_tipo   BIGINT        NOT NULL REFERENCES entrada_tipo (id),
    cantidad  INTEGER       NOT NULL,
    precio    NUMERIC(10,2) NOT NULL,
    CONSTRAINT entrada_compra_detalle_cantidad_valida CHECK (cantidad > 0)
);

CREATE INDEX IF NOT EXISTS entrada_compra_detalle_tipo_idx ON entrada_compra_detalle (id_tipo);

CREATE TABLE IF NOT EXISTS entrada_pase (
    id             BIGSERIAL PRIMARY KEY,
    id_compra      BIGINT      NOT NULL REFERENCES entrada_compra (id) ON DELETE CASCADE,
    id_tipo        BIGINT      NOT NULL REFERENCES entrada_tipo (id),
    numero         INTEGER     NOT NULL,
    codigo         VARCHAR(40) NOT NULL UNIQUE,
    dentro         BOOLEAN     NOT NULL DEFAULT FALSE,
    primer_ingreso TIMESTAMP,
    UNIQUE (id_compra, numero)
);

CREATE TABLE IF NOT EXISTS entrada_pase_acceso (
    id             BIGSERIAL PRIMARY KEY,
    id_pase        BIGINT    NOT NULL REFERENCES entrada_pase (id) ON DELETE CASCADE,
    sentido        CHAR(1)   NOT NULL,
    registrado_por BIGINT,
    cuando         TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT entrada_pase_acceso_sentido_valido CHECK (sentido IN ('E', 'S'))
);

CREATE INDEX IF NOT EXISTS entrada_pase_acceso_pase_idx ON entrada_pase_acceso (id_pase, cuando DESC);

COMMIT;

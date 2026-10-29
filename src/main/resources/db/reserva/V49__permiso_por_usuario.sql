BEGIN;

CREATE TABLE IF NOT EXISTS usuario_pantalla (
    id                   BIGSERIAL PRIMARY KEY,
    id_usuario           BIGINT NOT NULL,
    pantalla             VARCHAR(40) NOT NULL,
    _fecha_registro      TIMESTAMP NOT NULL DEFAULT now(),
    _registro_id_usuario BIGINT,
    UNIQUE (id_usuario, pantalla)
);

COMMENT ON TABLE usuario_pantalla IS 'Pantallas adicionales de un usuario, sumadas a las de su rol. En Inscripciones, Credenciales y Control de ventas tambien dan acceso a los datos (solo los propios si no es administracion).';

COMMIT;

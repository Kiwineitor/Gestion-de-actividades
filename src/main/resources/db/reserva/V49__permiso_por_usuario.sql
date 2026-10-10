-- =============================================================================
-- V49 - Pantallas por USUARIO, ademas de por rol.
--
-- Lo que ve un usuario = las pantallas de su rol + las que se le den a EL en esta tabla. Asi
-- se puede habilitar "Inscripciones" o "Credenciales" a una persona concreta sin cambiarle el
-- rol ni abrirselo a todos los de su rol.
--
-- Para Inscripciones, Credenciales y Control de ventas, tener la pantalla tambien da acceso a
-- sus datos en el servidor, con una condicion: administracion (y verificador/asesoria, que ya
-- lo veian todo) ve todas las ventas; cualquier otro usuario, solo las que registro el.
-- Lo aplica la aplicacion (security/AccesoPantallas), no la base.
--
-- Sin clave foranea a usuario a proposito: los usuarios nunca se borran fisicamente (baja
-- logica en usuario._estado), y declararla exige el privilegio REFERENCES que solo tiene el
-- dueño de la tabla. El servicio valida que el usuario exista antes de guardar.
--
-- Idempotente: se puede ejecutar varias veces.
-- =============================================================================
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

-- =============================================================================
-- Datos de PRUEBA para levantar UniFex en Docker y simular una feria de punta a punta.
--
-- Lo aplica docker/db/init/20-datos-prueba.sh UNA sola vez, al crear el volumen de la
-- base, y solo si CARGAR_DATOS_PRUEBA=true. Corre DESPUES del esquema (V0..V49) y ANTES
-- de que arranque Java. NUNCA contra produccion: inventa personas, ventas y contrasenas.
--
-- Que deja listo:
--   * 8 categorias con color/forma/precio y 2 opciones de precio cada una
--   * 196 casetas sobre el plano de ejemplo (frontend/public/mapa.png), libres,
--     menos 2 bloqueadas y las vendidas de abajo
--   * catalogo academico: carreras dentro de ACEF / ACBN / ACYT (las areas las crea V47)
--   * 5 tipos de entidad
--   * 4 usuarios de prueba, uno por rol de trabajo (admin1 y admin2 los crea Java al
--     arrancar, con ADMIN1_PASSWORD / ADMIN2_PASSWORD):
--         vendedor1 / vendedor2  ADMINISTRATIVO   contrasena: Fexpo2026
--         control1               CONTROL          contrasena: Fexpo2026
--         asesoria1              ASESORIA         contrasena: Fexpo2026
--   * casetas habilitadas a cada vendedor (algunas compartidas, como permite V32)
--   * pantallas por rol (rol_pantalla), alineadas con lo que el backend autoriza
--   * 3 ventas de ejemplo "por rendir" (sin comprobante adjunto), para que los tableros
--     no arranquen vacios y para practicar el rendido adjuntando el comprobante
--
-- Las contrasenas se guardan con BCrypt via pgcrypto (crypt + gen_salt('bf')): el
-- BCryptPasswordEncoder de Spring acepta esos hashes $2a$ tal cual.
-- =============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

-- ------------------------------------------------------------------ tipos de entidad
INSERT INTO tipo_entidad (nombre, _estado, _fecha_registro, _fecha_modificacion)
SELECT v.nombre, 'ACTIVO', now(), now()
  FROM (VALUES ('EMPRESA PRIVADA'), ('INSTITUCION PUBLICA'), ('EMPRENDEDOR'),
               ('UNIDAD ACADEMICA UAP'), ('ORGANIZACION SOCIAL / ONG')) AS v(nombre)
 WHERE NOT EXISTS (SELECT 1 FROM tipo_entidad t WHERE t.nombre = v.nombre);

-- ------------------------------------------------------------------ categorias
INSERT INTO categoria (nombre, descripcion, color, forma, precio_base, tamano_mapa,
                       _estado, _fecha_registro, _fecha_modificacion)
SELECT v.nombre, v.descripcion, v.color, v.forma, v.precio, 0.014, 'A', now(), now()
  FROM (VALUES
        ('COMIDA',          'Patio de comidas y bebidas',              '#ef4444', 'circulo',   1500.00),
        ('EMPRESAS',        'Empresas e instituciones',                '#3b82f6', 'cuadrado',  2500.00),
        ('MYPES',           'Micro y pequenas empresas',               '#22c55e', 'cuadrado',   800.00),
        ('ARTESANIAS',      'Artesania y manualidades',                '#a855f7', 'triangulo',  600.00),
        ('AGROPECUARIA',    'Produccion agropecuaria',                 '#84cc16', 'cuadrado',  1000.00),
        ('VIVERO',          'Plantas y jardineria',                    '#10b981', 'circulo',    500.00),
        ('VEHICULAR',       'Exposicion de vehiculos y maquinaria',    '#f97316', 'cuadrado',  3000.00),
        ('PROFESIOGRAFICA', 'Carreras de la UAP (sin costo)',          '#0ea5e9', 'cuadrado',     0.00)
       ) AS v(nombre, descripcion, color, forma, precio)
 WHERE NOT EXISTS (SELECT 1 FROM categoria c WHERE c.nombre = v.nombre);

-- Dos opciones de precio por categoria: la estandar (predeterminada, al precio base) y una
-- con energia electrica (+20 %). Exactamente una predeterminada viva por categoria (V47).
INSERT INTO categoria_opcion (id_categoria, nombre, precio, predeterminada, orden,
                              _estado, _fecha_registro, _fecha_modificacion)
SELECT c.id, o.nombre, round(c.precio_base * o.factor, 2), o.pred, o.orden, 'A', now(), now()
  FROM categoria c
 CROSS JOIN (VALUES ('Estandar', 1.0, true, 1), ('Con energia electrica', 1.2, false, 2))
            AS o(nombre, factor, pred, orden)
 WHERE c._estado <> 'X'
   AND NOT EXISTS (SELECT 1 FROM categoria_opcion x WHERE x.id_categoria = c.id AND x.nombre = o.nombre);

-- ------------------------------------------------------------------ casetas
-- (zona del plano, x, y) normalizados 0..1 sobre mapa.png: el centro de cada lote dibujado.
-- Las genera el mismo programa que dibuja el plano, asi que coinciden. Cada zona va entera a una
-- categoria; el codigo es correlativo y NUMERICO, porque fn_lista_puestos hace codigo::integer.
CREATE TEMP TABLE _punto (orden serial, seccion int, x double precision, y double precision);
INSERT INTO _punto (seccion, x, y) VALUES
  (0, 0.1300, 0.2350),
  (0, 0.1518, 0.2350),
  (0, 0.1736, 0.2350),
  (0, 0.1954, 0.2350),
  (0, 0.2171, 0.2350),
  (0, 0.2389, 0.2350),
  (0, 0.2607, 0.2350),
  (0, 0.2825, 0.2350),
  (0, 0.1300, 0.2518),
  (0, 0.1518, 0.2518),
  (0, 0.1736, 0.2518),
  (0, 0.1954, 0.2518),
  (0, 0.2171, 0.2518),
  (0, 0.2389, 0.2518),
  (0, 0.2607, 0.2518),
  (0, 0.2825, 0.2518),
  (0, 0.1300, 0.2687),
  (0, 0.1518, 0.2687),
  (0, 0.1736, 0.2687),
  (0, 0.1954, 0.2687),
  (0, 0.2171, 0.2687),
  (0, 0.2389, 0.2687),
  (0, 0.2607, 0.2687),
  (0, 0.2825, 0.2687),
  (0, 0.1300, 0.2855),
  (0, 0.1518, 0.2855),
  (0, 0.1736, 0.2855),
  (0, 0.1954, 0.2855),
  (0, 0.2171, 0.2855),
  (0, 0.2389, 0.2855),
  (0, 0.2607, 0.2855),
  (0, 0.2825, 0.2855),
  (1, 0.5800, 0.2350),
  (1, 0.6018, 0.2350),
  (1, 0.6236, 0.2350),
  (1, 0.6454, 0.2350),
  (1, 0.6671, 0.2350),
  (1, 0.6889, 0.2350),
  (1, 0.7107, 0.2350),
  (1, 0.7325, 0.2350),
  (1, 0.5800, 0.2518),
  (1, 0.6018, 0.2518),
  (1, 0.6236, 0.2518),
  (1, 0.6454, 0.2518),
  (1, 0.6671, 0.2518),
  (1, 0.6889, 0.2518),
  (1, 0.7107, 0.2518),
  (1, 0.7325, 0.2518),
  (1, 0.5800, 0.2687),
  (1, 0.6018, 0.2687),
  (1, 0.6236, 0.2687),
  (1, 0.6454, 0.2687),
  (1, 0.6671, 0.2687),
  (1, 0.6889, 0.2687),
  (1, 0.7107, 0.2687),
  (1, 0.7325, 0.2687),
  (1, 0.5800, 0.2855),
  (1, 0.6018, 0.2855),
  (1, 0.6236, 0.2855),
  (1, 0.6454, 0.2855),
  (1, 0.6671, 0.2855),
  (1, 0.6889, 0.2855),
  (1, 0.7107, 0.2855),
  (1, 0.7325, 0.2855),
  (2, 0.1300, 0.3800),
  (2, 0.1518, 0.3800),
  (2, 0.1736, 0.3800),
  (2, 0.1954, 0.3800),
  (2, 0.2171, 0.3800),
  (2, 0.2389, 0.3800),
  (2, 0.2607, 0.3800),
  (2, 0.2825, 0.3800),
  (2, 0.3043, 0.3800),
  (2, 0.3261, 0.3800),
  (2, 0.3479, 0.3800),
  (2, 0.3697, 0.3800),
  (2, 0.1300, 0.3968),
  (2, 0.1518, 0.3968),
  (2, 0.1736, 0.3968),
  (2, 0.1954, 0.3968),
  (2, 0.2171, 0.3968),
  (2, 0.2389, 0.3968),
  (2, 0.2607, 0.3968),
  (2, 0.2825, 0.3968),
  (2, 0.3043, 0.3968),
  (2, 0.3261, 0.3968),
  (2, 0.3479, 0.3968),
  (2, 0.3697, 0.3968),
  (2, 0.1300, 0.4137),
  (2, 0.1518, 0.4137),
  (2, 0.1736, 0.4137),
  (2, 0.1954, 0.4137),
  (2, 0.2171, 0.4137),
  (2, 0.2389, 0.4137),
  (2, 0.2607, 0.4137),
  (2, 0.2825, 0.4137),
  (2, 0.3043, 0.4137),
  (2, 0.3261, 0.4137),
  (2, 0.3479, 0.4137),
  (2, 0.3697, 0.4137),
  (2, 0.1300, 0.4305),
  (2, 0.1518, 0.4305),
  (2, 0.1736, 0.4305),
  (2, 0.1954, 0.4305),
  (2, 0.2171, 0.4305),
  (2, 0.2389, 0.4305),
  (2, 0.2607, 0.4305),
  (2, 0.2825, 0.4305),
  (2, 0.3043, 0.4305),
  (2, 0.3261, 0.4305),
  (2, 0.3479, 0.4305),
  (2, 0.3697, 0.4305),
  (3, 0.5800, 0.3800),
  (3, 0.6018, 0.3800),
  (3, 0.6236, 0.3800),
  (3, 0.6454, 0.3800),
  (3, 0.6671, 0.3800),
  (3, 0.6889, 0.3800),
  (3, 0.7107, 0.3800),
  (3, 0.7325, 0.3800),
  (3, 0.5800, 0.3968),
  (3, 0.6018, 0.3968),
  (3, 0.6236, 0.3968),
  (3, 0.6454, 0.3968),
  (3, 0.6671, 0.3968),
  (3, 0.6889, 0.3968),
  (3, 0.7107, 0.3968),
  (3, 0.7325, 0.3968),
  (3, 0.5800, 0.4137),
  (3, 0.6018, 0.4137),
  (3, 0.6236, 0.4137),
  (3, 0.6454, 0.4137),
  (3, 0.6671, 0.4137),
  (3, 0.6889, 0.4137),
  (3, 0.7107, 0.4137),
  (3, 0.7325, 0.4137),
  (4, 0.1300, 0.5300),
  (4, 0.1518, 0.5300),
  (4, 0.1736, 0.5300),
  (4, 0.1954, 0.5300),
  (4, 0.2171, 0.5300),
  (4, 0.2389, 0.5300),
  (4, 0.1300, 0.5468),
  (4, 0.1518, 0.5468),
  (4, 0.1736, 0.5468),
  (4, 0.1954, 0.5468),
  (4, 0.2171, 0.5468),
  (4, 0.2389, 0.5468),
  (4, 0.1300, 0.5637),
  (4, 0.1518, 0.5637),
  (4, 0.1736, 0.5637),
  (4, 0.1954, 0.5637),
  (4, 0.2171, 0.5637),
  (4, 0.2389, 0.5637),
  (4, 0.1300, 0.5805),
  (4, 0.1518, 0.5805),
  (4, 0.1736, 0.5805),
  (4, 0.1954, 0.5805),
  (4, 0.2171, 0.5805),
  (4, 0.2389, 0.5805),
  (5, 0.3600, 0.5300),
  (5, 0.3818, 0.5300),
  (5, 0.4036, 0.5300),
  (5, 0.4254, 0.5300),
  (5, 0.3600, 0.5468),
  (5, 0.3818, 0.5468),
  (5, 0.4036, 0.5468),
  (5, 0.4254, 0.5468),
  (5, 0.3600, 0.5637),
  (5, 0.3818, 0.5637),
  (5, 0.4036, 0.5637),
  (5, 0.4254, 0.5637),
  (6, 0.5800, 0.5300),
  (6, 0.6018, 0.5300),
  (6, 0.6236, 0.5300),
  (6, 0.6454, 0.5300),
  (6, 0.5800, 0.5468),
  (6, 0.6018, 0.5468),
  (6, 0.6236, 0.5468),
  (6, 0.6454, 0.5468),
  (7, 0.5800, 0.6200),
  (7, 0.6018, 0.6200),
  (7, 0.6236, 0.6200),
  (7, 0.6454, 0.6200),
  (7, 0.6671, 0.6200),
  (7, 0.6889, 0.6200),
  (7, 0.7107, 0.6200),
  (7, 0.7325, 0.6200),
  (7, 0.5800, 0.6368),
  (7, 0.6018, 0.6368),
  (7, 0.6236, 0.6368),
  (7, 0.6454, 0.6368),
  (7, 0.6671, 0.6368),
  (7, 0.6889, 0.6368),
  (7, 0.7107, 0.6368),
  (7, 0.7325, 0.6368)
;

CREATE TEMP TABLE _seccion_categoria (seccion int, categoria text);
INSERT INTO _seccion_categoria VALUES
  (0, 'COMIDA'), (1, 'EMPRESAS'), (2, 'MYPES'), (3, 'ARTESANIAS'),
  (4, 'AGROPECUARIA'), (5, 'VIVERO'), (6, 'VEHICULAR'), (7, 'PROFESIOGRAFICA');

INSERT INTO puesto (codigo, tamano, id_categoria, estado_puesto, mapa_x, mapa_y, mapa_escala,
                    version, _estado, _fecha_registro, _fecha_modificacion)
SELECT p.orden::text, '3x3', c.id, 'L', p.x, p.y, 1, 0, 'A', now(), now()
  FROM _punto p
  JOIN _seccion_categoria sc ON sc.seccion = p.seccion
  JOIN categoria c ON c.nombre = sc.categoria
 WHERE NOT EXISTS (SELECT 1 FROM puesto x WHERE x.codigo = p.orden::text)
 ORDER BY p.orden;

-- Dos casetas bloqueadas "por reparacion" (solo estado_puesto: siguen vivas y se pueden
-- desbloquear desde el editor), para ver ese estado en el mapa.
UPDATE puesto SET estado_puesto = 'X' WHERE codigo IN ('120', '121') AND estado_puesto = 'L';

-- ------------------------------------------------------------------ catalogo academico
INSERT INTO carrera (id_area, nombre, _estado, _fecha_registro, _fecha_modificacion)
SELECT a.id, v.nombre, 'A', now(), now()
  FROM (VALUES
        ('ACYT', 'INGENIERIA DE SISTEMAS'), ('ACYT', 'INGENIERIA CIVIL'),
        ('ACEF', 'CONTADURIA PUBLICA'),     ('ACEF', 'ADMINISTRACION DE EMPRESAS'),
        ('ACBN', 'MEDICINA'),               ('ACBN', 'ENFERMERIA')
       ) AS v(sigla, nombre)
  JOIN area a ON a.sigla = v.sigla AND a._estado <> 'X'
 WHERE NOT EXISTS (SELECT 1 FROM carrera c WHERE c.nombre = v.nombre);

-- ------------------------------------------------------------------ usuarios de prueba
INSERT INTO persona (nombre, paterno, materno, ci, correo, celular, id_carrera,
                     _estado, _fecha_registro, _fecha_modificacion)
SELECT v.nombre, v.paterno, v.materno, v.ci, v.correo, v.celular,
       (SELECT id FROM carrera WHERE nombre = v.carrera), 'ACTIVO', now(), now()
  FROM (VALUES
        ('MARIA',  'VARGAS',  'QUISPE', '7000001', 'vendedor1@prueba.local', '70000001', 'INGENIERIA DE SISTEMAS'),
        ('CARLOS', 'MAMANI',  'ROCA',   '7000002', 'vendedor2@prueba.local', '70000002', 'CONTADURIA PUBLICA'),
        ('JORGE',  'SUAREZ',  'PEÑA',   '7000003', 'control1@prueba.local',  '70000003', NULL),
        ('LUCIA',  'ORTIZ',   'CRUZ',   '7000004', 'asesoria1@prueba.local', '70000004', NULL)
       ) AS v(nombre, paterno, materno, ci, correo, celular, carrera)
 WHERE NOT EXISTS (SELECT 1 FROM persona p WHERE p.ci = v.ci);

INSERT INTO usuario (username, password, persona_id, rol_id, _estado, _fecha_registro, _fecha_modificacion)
SELECT v.username, crypt('Fexpo2026', gen_salt('bf', 10)),
       (SELECT id FROM persona WHERE ci = v.ci),
       (SELECT id FROM rol WHERE nombre = v.rol), 'ACTIVO', now(), now()
  FROM (VALUES
        ('vendedor1', '7000001', 'ADMINISTRATIVO'),
        ('vendedor2', '7000002', 'ADMINISTRATIVO'),
        ('control1',  '7000003', 'CONTROL'),
        ('asesoria1', '7000004', 'ASESORIA')
       ) AS v(username, ci, rol)
 WHERE NOT EXISTS (SELECT 1 FROM usuario u WHERE u.username = v.username);

-- La asesora monitorea la facultad ACYT (Seguimiento por facultad, V41).
UPDATE usuario SET id_area_seguimiento = (SELECT id FROM area WHERE sigla = 'ACYT')
 WHERE username = 'asesoria1' AND id_area_seguimiento IS NULL;

-- ------------------------------------------------------------------ casetas por vendedor
-- Un vendedor solo vende las casetas que tiene habilitadas (V23). COMIDA se comparte
-- entre los dos (V32); ARTESANIAS es solo de vendedor1; MYPES y EMPRESAS solo de vendedor2.
INSERT INTO vendedor_puesto (id_usuario, id_puesto, _fecha_registro)
SELECT u.id, p.id, now()
  FROM (VALUES ('vendedor1', 'COMIDA'), ('vendedor1', 'ARTESANIAS'),
               ('vendedor2', 'COMIDA'), ('vendedor2', 'MYPES'), ('vendedor2', 'EMPRESAS')) AS v(username, categoria)
  JOIN usuario u   ON u.username = v.username
  JOIN categoria c ON c.nombre = v.categoria
  JOIN puesto p    ON p.id_categoria = c.id AND p._estado <> 'X'
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------------ pantallas por rol
-- SUPER USUARIO no figura: lo ve todo siempre (PermisoPantallaService.loVeTodo).
-- Cada pantalla dada aqui esta autorizada tambien en el backend para ese rol (security/Roles).
INSERT INTO rol_pantalla (id_rol, pantalla)
SELECT r.id, v.pantalla
  FROM (VALUES
        -- Administracion de la feria: todo menos mantenimiento (queda para SUPER USUARIO)
        ('ADMINISTRADOR', 'inicio'), ('ADMINISTRADOR', 'mapa'), ('ADMINISTRADOR', 'venta'),
        ('ADMINISTRADOR', 'mis-ventas'), ('ADMINISTRADOR', 'catalogo'), ('ADMINISTRADOR', 'tablero'),
        ('ADMINISTRADOR', 'editor'), ('ADMINISTRADOR', 'categorias'), ('ADMINISTRADOR', 'puestos'),
        ('ADMINISTRADOR', 'seguimiento'), ('ADMINISTRADOR', 'anuncios'),
        ('ADMINISTRADOR', 'seguimiento-facultad'), ('ADMINISTRADOR', 'responsables-extra'),
        ('ADMINISTRADOR', 'vendedores'), ('ADMINISTRADOR', 'inscripciones'),
        ('ADMINISTRADOR', 'interesados'), ('ADMINISTRADOR', 'direccion'), ('ADMINISTRADOR', 'reportes'),
        ('ADMINISTRADOR', 'control-ventas'), ('ADMINISTRADOR', 'reporte-responsables'),
        ('ADMINISTRADOR', 'noches-fexpo'), ('ADMINISTRADOR', 'noticias'), ('ADMINISTRADOR', 'credenciales'),
        ('ADMINISTRADOR', 'credenciales-apoyo'), ('ADMINISTRADOR', 'impresion-masiva'),
        ('ADMINISTRADOR', 'escaner'), ('ADMINISTRADOR', 'control-acceso'), ('ADMINISTRADOR', 'usuarios'),
        ('ADMINISTRADOR', 'roles'), ('ADMINISTRADOR', 'permisos'), ('ADMINISTRADOR', 'personas'),
        ('ADMINISTRADOR', 'personal-apoyo'), ('ADMINISTRADOR', 'notificaciones'),
        -- Vendedor: vende, ve lo suyo y saca las credenciales de sus ventas
        ('ADMINISTRATIVO', 'inicio'), ('ADMINISTRATIVO', 'mapa'), ('ADMINISTRATIVO', 'venta'),
        ('ADMINISTRATIVO', 'mis-ventas'), ('ADMINISTRATIVO', 'catalogo'),
        ('ADMINISTRATIVO', 'inscripciones'), ('ADMINISTRATIVO', 'credenciales'),
        ('ADMINISTRATIVO', 'notificaciones'),
        -- Puerta: escanea credenciales y registra entradas/salidas
        ('CONTROL', 'inicio'), ('CONTROL', 'escaner'), ('CONTROL', 'control-acceso'),
        ('CONTROL', 'notificaciones'),
        -- Asesoria: consulta, sin modificar
        ('ASESORIA', 'inicio'), ('ASESORIA', 'catalogo'), ('ASESORIA', 'tablero'),
        ('ASESORIA', 'inscripciones'), ('ASESORIA', 'control-ventas'),
        ('ASESORIA', 'seguimiento-facultad'), ('ASESORIA', 'notificaciones')
       ) AS v(rol, pantalla)
  JOIN rol r ON r.nombre = v.rol
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------------ ventas de ejemplo
-- Replican lo que escribe RegistroVentaService: entidad -> personas/responsables ->
-- inscripcion (edicion activa, registrada por el vendedor) -> inscripcion_puesto con el
-- costo congelado y la opcion predeterminada -> caseta en 'O'. Sin img_comprobante: quedan
-- "por rendir" en Control de ventas hasta que alguien adjunte el comprobante desde la ficha.
CREATE TEMP TABLE _venta (n int, vendedor text, tipo text, entidad text, nit text,
                          representante text, ci_rep text, celular text, contado boolean,
                          banco text, comprobante text, casetas text[], hace interval);
INSERT INTO _venta VALUES
  (1, 'vendedor1', 'EMPRENDEDOR',     'POLLOS A LA BRASA DON BETO', NULL,
      'ROBERTO AÑEZ SALVATIERRA', '4100001', '71100001', true,  NULL,  NULL,     ARRAY['1'],         '3 days'),
  (2, 'vendedor2', 'EMPRESA PRIVADA', 'DISTRIBUIDORA AMAZONIA SRL', '102030405',
      'PATRICIA MENDEZ ROJAS',    '4100002', '71100002', false, 'BNB', '784512', ARRAY['33', '34'], '2 days'),
  (3, 'vendedor1', 'EMPRENDEDOR',     'ARTESANIAS TACANA',          NULL,
      'ELENA CHAO MERCADO',       '4100003', '71100003', true,  NULL,  NULL,     ARRAY['114'],        '1 day');

DO $$
DECLARE
    v          record;
    v_vendedor bigint;
    v_entidad  bigint;
    v_insc     bigint;
    v_persona  bigint;
    v_codigo   text;
    v_puesto   record;
    v_opcion   record;
    v_fecha    timestamp;
    v_edicion  bigint := (SELECT id FROM edicion WHERE activa ORDER BY id DESC LIMIT 1);
BEGIN
    FOR v IN SELECT * FROM _venta ORDER BY n LOOP
        IF EXISTS (SELECT 1 FROM entidad WHERE nombre = v.entidad) THEN
            CONTINUE;
        END IF;
        v_vendedor := (SELECT id FROM usuario WHERE username = v.vendedor);
        v_fecha    := now() - v.hace;

        INSERT INTO entidad (nombre, nit, representante_legal, ci_representante, celular_representante,
                             descripcion, objeto, id_tipo_entidad, _estado,
                             _fecha_registro, _registro_id_usuario, _fecha_modificacion, _modificacion_id_usuario)
        VALUES (v.entidad, v.nit, v.representante, v.ci_rep, v.celular,
                'Venta de ejemplo (datos de prueba)', 'Exposicion y venta',
                (SELECT id FROM tipo_entidad WHERE nombre = v.tipo), 'ACTIVO',
                v_fecha, v_vendedor, v_fecha, v_vendedor)
        RETURNING id INTO v_entidad;

        -- Responsables: el representante (titular) y un ayudante, dos por caseta como maximo.
        FOR i IN 1..2 LOOP
            INSERT INTO persona (nombre, paterno, materno, ci, celular, _estado,
                                 _fecha_registro, _registro_id_usuario, _fecha_modificacion, _modificacion_id_usuario)
            VALUES (CASE WHEN i = 1 THEN split_part(v.representante, ' ', 1) ELSE 'AYUDANTE ' || v.n END,
                    CASE WHEN i = 1 THEN split_part(v.representante, ' ', 2) ELSE 'PRUEBA' END,
                    CASE WHEN i = 1 THEN split_part(v.representante, ' ', 3) ELSE NULL END,
                    CASE WHEN i = 1 THEN v.ci_rep ELSE '42000' || v.n END,
                    v.celular, 'RESPONSABLE', v_fecha, v_vendedor, v_fecha, v_vendedor)
            RETURNING id INTO v_persona;

            INSERT INTO responsable (id_entidad, id_persona, es_titular, es_extra, _estado,
                                     _fecha_registro, _registro_id_usuario, _fecha_modificacion, _modificacion_id_usuario)
            VALUES (v_entidad, v_persona, i = 1, false, 'RESPONSABLE', v_fecha, v_vendedor, v_fecha, v_vendedor);
        END LOOP;

        INSERT INTO inscripcion (id_entidad, id_edicion, fecha_compra, inscripcion_estado, pago_contado,
                                 entidad_bancaria, num_comprobante, _estado,
                                 _fecha_registro, _registro_id_usuario, _fecha_modificacion, _modificacion_id_usuario)
        VALUES (v_entidad, v_edicion, v_fecha, 'PENDIENTE', v.contado, v.banco, v.comprobante, 'ACTIVO',
                v_fecha, v_vendedor, v_fecha, v_vendedor)
        RETURNING id INTO v_insc;

        FOREACH v_codigo IN ARRAY v.casetas LOOP
            SELECT id, id_categoria, precio INTO v_puesto FROM puesto WHERE codigo = v_codigo;
            SELECT id, precio INTO v_opcion FROM categoria_opcion
             WHERE id_categoria = v_puesto.id_categoria AND predeterminada AND _estado <> 'X';

            INSERT INTO inscripcion_puesto (id_inscripcion, id_puesto, costo, id_categoria_opcion, _estado,
                                            _fecha_registro, _registro_id_usuario, _fecha_modificacion, _modificacion_id_usuario)
            VALUES (v_insc, v_puesto.id, coalesce(v_puesto.precio, v_opcion.precio), v_opcion.id, 'ACTIVO',
                    v_fecha, v_vendedor, v_fecha, v_vendedor);

            UPDATE puesto SET estado_puesto = 'O', version = version + 1,
                              _fecha_modificacion = v_fecha, _modificacion_id_usuario = v_vendedor
             WHERE id = v_puesto.id;
        END LOOP;
    END LOOP;
END $$;

COMMIT;

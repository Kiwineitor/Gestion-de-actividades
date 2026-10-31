BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

INSERT INTO tipo_entidad (nombre, _estado, _fecha_registro, _fecha_modificacion)
SELECT v.nombre, 'ACTIVO', now(), now()
  FROM (VALUES ('EMPRESA PRIVADA'), ('INSTITUCION PUBLICA'), ('EMPRENDEDOR'),
               ('UNIDAD ACADEMICA UAP'), ('ORGANIZACION SOCIAL / ONG')) AS v(nombre)
 WHERE NOT EXISTS (SELECT 1 FROM tipo_entidad t WHERE t.nombre = v.nombre);

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

INSERT INTO categoria_opcion (id_categoria, nombre, precio, predeterminada, orden,
                              _estado, _fecha_registro, _fecha_modificacion)
SELECT c.id, o.nombre, round(c.precio_base * o.factor, 2), o.pred, o.orden, 'A', now(), now()
  FROM categoria c
 CROSS JOIN (VALUES ('Estandar', 1.0, true, 1), ('Con energia electrica', 1.2, false, 2))
            AS o(nombre, factor, pred, orden)
 WHERE c._estado <> 'X'
   AND NOT EXISTS (SELECT 1 FROM categoria_opcion x WHERE x.id_categoria = c.id AND x.nombre = o.nombre);

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

UPDATE puesto SET estado_puesto = 'X' WHERE codigo IN ('120', '121') AND estado_puesto = 'L';

INSERT INTO carrera (id_area, nombre, _estado, _fecha_registro, _fecha_modificacion)
SELECT a.id, v.nombre, 'A', now(), now()
  FROM (VALUES
        ('ACYT', 'INGENIERIA DE SISTEMAS'), ('ACYT', 'INGENIERIA CIVIL'),
        ('ACEF', 'CONTADURIA PUBLICA'),     ('ACEF', 'ADMINISTRACION DE EMPRESAS'),
        ('ACBN', 'MEDICINA'),               ('ACBN', 'ENFERMERIA')
       ) AS v(sigla, nombre)
  JOIN area a ON a.sigla = v.sigla AND a._estado <> 'X'
 WHERE NOT EXISTS (SELECT 1 FROM carrera c WHERE c.nombre = v.nombre);

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

UPDATE usuario SET id_area_seguimiento = (SELECT id FROM area WHERE sigla = 'ACYT')
 WHERE username = 'asesoria1' AND id_area_seguimiento IS NULL;

INSERT INTO vendedor_puesto (id_usuario, id_puesto, _fecha_registro)
SELECT u.id, p.id, now()
  FROM (VALUES ('vendedor1', 'COMIDA'), ('vendedor1', 'ARTESANIAS'),
               ('vendedor2', 'COMIDA'), ('vendedor2', 'MYPES'), ('vendedor2', 'EMPRESAS')) AS v(username, categoria)
  JOIN usuario u   ON u.username = v.username
  JOIN categoria c ON c.nombre = v.categoria
  JOIN puesto p    ON p.id_categoria = c.id AND p._estado <> 'X'
ON CONFLICT DO NOTHING;

INSERT INTO rol_pantalla (id_rol, pantalla)
SELECT r.id, v.pantalla
  FROM (VALUES
        ('ADMINISTRADOR', 'inicio'), ('ADMINISTRADOR', 'mapa'),
        ('ADMINISTRADOR', 'mis-ventas'), ('ADMINISTRADOR', 'catalogo'),
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
        ('ADMINISTRATIVO', 'inicio'), ('ADMINISTRATIVO', 'mapa'),
        ('ADMINISTRATIVO', 'mis-ventas'), ('ADMINISTRATIVO', 'catalogo'),
        ('ADMINISTRATIVO', 'inscripciones'), ('ADMINISTRATIVO', 'credenciales'),
        ('ADMINISTRATIVO', 'notificaciones'),
        ('CONTROL', 'inicio'), ('CONTROL', 'escaner'), ('CONTROL', 'control-acceso'),
        ('CONTROL', 'notificaciones'),
        ('ASESORIA', 'inicio'), ('ASESORIA', 'catalogo'),
        ('ASESORIA', 'inscripciones'), ('ASESORIA', 'control-ventas'),
        ('ASESORIA', 'seguimiento-facultad'), ('ASESORIA', 'notificaciones')
       ) AS v(rol, pantalla)
  JOIN rol r ON r.nombre = v.rol
ON CONFLICT DO NOTHING;

INSERT INTO mesa_evento (nombre, local_nombre, local_direccion, descripcion, fecha_inicio, hora_limite, minutos_bloqueo,
                         pago_banco, pago_cuenta, pago_titular, pago_instrucciones, celular_contacto, politicas,
                         publicado, tipos_pase, plano_elementos)
SELECT 'Noche Neón', 'Euphoria Club', 'Av. Ejemplo 123 (local de prueba)', 'Evento de ejemplo para probar la reserva directa de mesas.', date_trunc('day', now()) + interval '30 days' + '22:00:00'::interval,
       date_trunc('day', now()) + interval '30 days' + '23:30:00'::interval, 5, 'Banco de Ejemplo', '1000-2000-3000', 'Euphoria Club (cuenta ficticia)', 'Transfiere el monto exacto de tu mesa. Es una cuenta ficticia de ejemplo.', '70000000', NULL, TRUE, NULL, '[{"id":"ezonavip","tipo":"texto","texto":"ZONA VIP","x":0.03,"y":0.05,"ancho":0.15,"alto":0.06,"color":"#ff4fa3","fondo":null,"tamano":0.018,"rotacion":0.0},{"id":"eescenario","tipo":"zona","texto":"ESCENARIO · DJ uwur","x":0.326,"y":0.042,"ancho":0.353,"alto":0.114,"color":"#e4dcff","fondo":"#7c5cff","tamano":0.024,"rotacion":0.0},{"id":"epista","tipo":"zona","texto":"PISTA","x":0.294,"y":0.334,"ancho":0.416,"alto":0.336,"color":"#9a96c8","fondo":"#6b6f9e","tamano":0.022,"rotacion":0.0},{"id":"ebarra","tipo":"zona","texto":"BARRA","x":0.862,"y":0.222,"ancho":0.097,"alto":0.569,"color":"#8ce6d6","fondo":"#2bb5a0","tamano":0.018,"rotacion":0.0},{"id":"eingreso","tipo":"zona","texto":"INGRESO","x":0.438,"y":0.905,"ancho":0.128,"alto":0.076,"color":"#aab3cc","fondo":"#6b7a99","tamano":0.016,"rotacion":0.0}]'
 WHERE NOT EXISTS (SELECT 1 FROM mesa_evento WHERE nombre = 'Noche Neón');

INSERT INTO mesa (id_evento, codigo, zona, capacidad, precio, beneficios, forma, color, mapa_x, mapa_y, tamano)
SELECT e.id, v.codigo, v.zona, v.capacidad, v.precio, v.beneficios, v.forma, v.color, v.mapa_x, v.mapa_y, v.tamano
  FROM mesa_evento e, (VALUES
    ('V1', 'VIP', 8, 800.00::numeric, '1 botella premium + 6 mixers + hielo', 'rectangulo', '#ff6fb5', 0.12::float8, 0.3::float8, 0.07::float8),
    ('V2', 'VIP', 8, 800.00::numeric, '1 botella premium + 6 mixers + hielo', 'rectangulo', '#ff6fb5', 0.21::float8, 0.3::float8, 0.07::float8),
    ('V3', 'VIP', 8, 800.00::numeric, '1 botella premium + 6 mixers + hielo', 'rectangulo', '#ff6fb5', 0.12::float8, 0.52::float8, 0.07::float8),
    ('V4', 'VIP', 8, 800.00::numeric, '1 botella premium + 6 mixers + hielo', 'rectangulo', '#ff6fb5', 0.21::float8, 0.52::float8, 0.07::float8),
    ('V5', 'VIP', 8, 800.00::numeric, '1 botella premium + 6 mixers + hielo', 'rectangulo', '#ff6fb5', 0.12::float8, 0.74::float8, 0.07::float8),
    ('V6', 'VIP', 8, 800.00::numeric, '1 botella premium + 6 mixers + hielo', 'rectangulo', '#ff6fb5', 0.21::float8, 0.74::float8, 0.07::float8),
    ('1', 'General', 5, 400.00::numeric, '1 botella + 4 mixers', 'circulo', '#6d5bd0', 0.32::float8, 0.25::float8, 0.05::float8),
    ('2', 'General', 5, 400.00::numeric, '1 botella + 4 mixers', 'circulo', '#6d5bd0', 0.392::float8, 0.25::float8, 0.05::float8),
    ('3', 'General', 5, 400.00::numeric, '1 botella + 4 mixers', 'circulo', '#6d5bd0', 0.46399999999999997::float8, 0.25::float8, 0.05::float8),
    ('4', 'General', 5, 400.00::numeric, '1 botella + 4 mixers', 'circulo', '#6d5bd0', 0.536::float8, 0.25::float8, 0.05::float8),
    ('5', 'General', 5, 400.00::numeric, '1 botella + 4 mixers', 'circulo', '#6d5bd0', 0.608::float8, 0.25::float8, 0.05::float8),
    ('6', 'General', 5, 400.00::numeric, '1 botella + 4 mixers', 'circulo', '#6d5bd0', 0.6799999999999999::float8, 0.25::float8, 0.05::float8),
    ('7', 'General', 5, 400.00::numeric, '1 botella + 4 mixers', 'circulo', '#6d5bd0', 0.32::float8, 0.76::float8, 0.05::float8),
    ('8', 'General', 5, 400.00::numeric, '1 botella + 4 mixers', 'circulo', '#6d5bd0', 0.392::float8, 0.76::float8, 0.05::float8),
    ('9', 'General', 5, 400.00::numeric, '1 botella + 4 mixers', 'circulo', '#6d5bd0', 0.46399999999999997::float8, 0.76::float8, 0.05::float8),
    ('10', 'General', 5, 400.00::numeric, '1 botella + 4 mixers', 'circulo', '#6d5bd0', 0.536::float8, 0.76::float8, 0.05::float8),
    ('11', 'General', 5, 400.00::numeric, '1 botella + 4 mixers', 'circulo', '#6d5bd0', 0.608::float8, 0.758::float8, 0.05::float8),
    ('12', 'General', 5, 400.00::numeric, '1 botella + 4 mixers', 'circulo', '#6d5bd0', 0.676::float8, 0.762::float8, 0.05::float8),
    ('B1', 'Barra', 4, 250.00::numeric, '2 jarras de cerveza', 'cuadrado', '#2f8f8a', 0.8::float8, 0.28::float8, 0.045::float8),
    ('B2', 'Barra', 4, 250.00::numeric, '2 jarras de cerveza', 'cuadrado', '#2f8f8a', 0.8::float8, 0.43::float8, 0.045::float8),
    ('B3', 'Barra', 4, 250.00::numeric, '2 jarras de cerveza', 'cuadrado', '#2f8f8a', 0.8::float8, 0.58::float8, 0.045::float8),
    ('B4', 'Barra', 4, 250.00::numeric, '2 jarras de cerveza', 'cuadrado', '#2f8f8a', 0.8::float8, 0.73::float8, 0.045::float8)
  ) AS v(codigo, zona, capacidad, precio, beneficios, forma, color, mapa_x, mapa_y, tamano)
 WHERE e.nombre = 'Noche Neón' AND NOT EXISTS (SELECT 1 FROM mesa m WHERE m.id_evento = e.id);

UPDATE categoria
   SET id_edicion = (SELECT id FROM edicion WHERE activa ORDER BY anio DESC, id DESC LIMIT 1)
 WHERE id_edicion IS NULL;

UPDATE edicion SET nombre = 'Feria de ejemplo ' || anio WHERE nombre ILIKE 'FEXPO%';

COMMIT;

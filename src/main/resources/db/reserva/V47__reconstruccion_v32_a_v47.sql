-- =============================================================================
-- V47 -- Reconstruccion de las migraciones V32..V47, que no llegaron al repositorio.
--
-- Por que existe: este repositorio se subio con V0..V31, V48 y V49, pero sin V32..V47.
-- Una base creada desde cero con solo esos archivos NO coincide con lo que espera el
-- codigo Java: faltan 10 tablas y 10 columnas, y la primera consulta de Hibernate sobre
-- `usuario` o `persona` falla con "no existe la columna" (es decir, ni siquiera se puede
-- iniciar sesion). La base real (bd_FexpoUapV2) si las tiene aplicadas.
--
-- Se dedujo del codigo (entidades JPA, SQL nativo de los DAO y los comentarios que citan
-- cada Vnn), no de un pg_dump: tipos y longitudes son razonables, no necesariamente los
-- exactos de produccion. Si algun dia aparecen los V32..V47 originales, se agregan al lado
-- y este archivo se puede borrar; mientras tanto no estorba, porque es IDEMPOTENTE de
-- punta a punta (IF NOT EXISTS / guardas en cada sentencia): sobre una base que ya tenga
-- los originales, no cambia nada.
--
-- Que trae, por migracion original:
--   V32  inscripcion.num_comprobante BIGINT -> VARCHAR(100); una caseta habilitada a
--        varios vendedores (indice unico por PAREJA, no por caseta)
--   V33  categoria_opcion (opciones de precio) + inscripcion_puesto.id_categoria_opcion
--   V34  responsable extra cobrado aparte (es_extra, monto_extra, comprobante_extra)
--   V35  area / carrera (catalogo academico) + persona.id_carrera; siembra ACEF/ACBN/ACYT
--   V37  puesto.precio (precio propio de una caseta)
--   V38  noche_fexpo.audio_archivo + contador_visitas
--   V41  usuario.id_area_seguimiento (facultad que monitorea)
--   V42  noticia
--   V43/V44  personal_apoyo.descripcion_tarea
--   V45  apoyo_acceso (entradas/salidas del personal de apoyo)
--   V47  personal_apoyo.foto
--   sin numero conocido: anuncio, plano_texto, credencial_acceso, credencial_impresion
-- =============================================================================

BEGIN;

-- ---------------------------------------------------------------- V32
-- Numero de comprobante como texto (admite letras y ceros iniciales). Solo si aun es numerico.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
              WHERE table_schema = 'public' AND table_name = 'inscripcion'
                AND column_name = 'num_comprobante' AND data_type <> 'character varying') THEN
    ALTER TABLE public.inscripcion
      ALTER COLUMN num_comprobante TYPE varchar(100) USING num_comprobante::text;
  END IF;
END $$;

-- Una caseta puede estar habilitada a varios vendedores: fuera el unico por caseta (V20),
-- dentro el unico por pareja, que es sobre el que actua el ON CONFLICT DO NOTHING del DAO.
DROP INDEX IF EXISTS public.ux_vendedor_puesto_caseta;
CREATE UNIQUE INDEX IF NOT EXISTS ux_vendedor_puesto_pareja
    ON public.vendedor_puesto (id_usuario, id_puesto);

-- ---------------------------------------------------------------- V35
CREATE TABLE IF NOT EXISTS public.area (
    id                       BIGSERIAL PRIMARY KEY,
    sigla                    VARCHAR(20)  NOT NULL,
    nombre                   VARCHAR(150),
    orden                    INTEGER,
    _fecha_registro          TIMESTAMP(6),
    _registro_id_usuario     BIGINT,
    _fecha_modificacion      TIMESTAMP(6),
    _modificacion_id_usuario BIGINT,
    _estado                  VARCHAR(1) NOT NULL DEFAULT 'A'
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_area_sigla_viva
    ON public.area (upper(sigla)) WHERE _estado <> 'X';

CREATE TABLE IF NOT EXISTS public.carrera (
    id                       BIGSERIAL PRIMARY KEY,
    id_area                  BIGINT NOT NULL REFERENCES public.area(id),
    nombre                   VARCHAR(150) NOT NULL,
    _fecha_registro          TIMESTAMP(6),
    _registro_id_usuario     BIGINT,
    _fecha_modificacion      TIMESTAMP(6),
    _modificacion_id_usuario BIGINT,
    _estado                  VARCHAR(1) NOT NULL DEFAULT 'A'
);
CREATE INDEX IF NOT EXISTS ix_carrera_area ON public.carrera (id_area);

ALTER TABLE public.persona ADD COLUMN IF NOT EXISTS id_carrera BIGINT REFERENCES public.carrera(id);

-- Las tres areas academicas. El nombre largo es igual a la sigla a proposito: la SPA
-- (stores/academico.js) solo muestra "SIGLA · nombre" cuando alguien rellena uno distinto.
INSERT INTO public.area (sigla, nombre, orden, _fecha_registro, _fecha_modificacion, _estado)
SELECT v.sigla, v.sigla, v.orden, now(), now(), 'A'
  FROM (VALUES ('ACEF', 1), ('ACBN', 2), ('ACYT', 3)) AS v(sigla, orden)
 WHERE NOT EXISTS (SELECT 1 FROM public.area a WHERE upper(a.sigla) = v.sigla AND a._estado <> 'X');

-- ---------------------------------------------------------------- V41
ALTER TABLE public.usuario ADD COLUMN IF NOT EXISTS id_area_seguimiento BIGINT REFERENCES public.area(id);

-- ---------------------------------------------------------------- V33
CREATE TABLE IF NOT EXISTS public.categoria_opcion (
    id                       BIGSERIAL PRIMARY KEY,
    id_categoria             BIGINT NOT NULL REFERENCES public.categoria(id),
    nombre                   VARCHAR(120) NOT NULL,
    precio                   NUMERIC(10,2) NOT NULL DEFAULT 0,
    predeterminada           BOOLEAN NOT NULL DEFAULT false,
    orden                    INTEGER,
    _fecha_registro          TIMESTAMP(6),
    _registro_id_usuario     BIGINT,
    _fecha_modificacion      TIMESTAMP(6),
    _modificacion_id_usuario BIGINT,
    _estado                  VARCHAR(1) NOT NULL DEFAULT 'A'
);
CREATE INDEX IF NOT EXISTS ix_categoria_opcion_categoria ON public.categoria_opcion (id_categoria);
-- Exactamente una predeterminada viva por categoria (CategoriaOpcionService desmarca antes de marcar).
CREATE UNIQUE INDEX IF NOT EXISTS ux_categoria_opcion_predeterminada
    ON public.categoria_opcion (id_categoria) WHERE predeterminada AND _estado <> 'X';

ALTER TABLE public.inscripcion_puesto
    ADD COLUMN IF NOT EXISTS id_categoria_opcion BIGINT REFERENCES public.categoria_opcion(id);

-- ---------------------------------------------------------------- V34
ALTER TABLE public.responsable ADD COLUMN IF NOT EXISTS es_extra BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.responsable ADD COLUMN IF NOT EXISTS monto_extra NUMERIC(10,2);
ALTER TABLE public.responsable ADD COLUMN IF NOT EXISTS comprobante_extra VARCHAR(300);

-- ---------------------------------------------------------------- V37
ALTER TABLE public.puesto ADD COLUMN IF NOT EXISTS precio NUMERIC(10,2);

-- ---------------------------------------------------------------- V38
ALTER TABLE public.noche_fexpo ADD COLUMN IF NOT EXISTS audio_archivo VARCHAR(300);

CREATE TABLE IF NOT EXISTS public.contador_visitas (
    pagina        VARCHAR(60) PRIMARY KEY,
    total         BIGINT NOT NULL DEFAULT 0,
    ultima_visita TIMESTAMP
);

-- ---------------------------------------------------------------- V42
CREATE TABLE IF NOT EXISTS public.noticia (
    id                       BIGSERIAL PRIMARY KEY,
    id_edicion               BIGINT NOT NULL REFERENCES public.edicion(id),
    fecha                    DATE NOT NULL,
    dia                      INTEGER NOT NULL CHECK (dia BETWEEN 1 AND 3),
    titulo                   VARCHAR(160) NOT NULL,
    texto                    VARCHAR(2000) NOT NULL,
    medio_tipo               VARCHAR(10) NOT NULL,
    medio_archivo            VARCHAR(300) NOT NULL,
    _fecha_registro          TIMESTAMP(6),
    _registro_id_usuario     BIGINT,
    _fecha_modificacion      TIMESTAMP(6),
    _modificacion_id_usuario BIGINT,
    _estado                  VARCHAR(1) NOT NULL DEFAULT 'A'
);
CREATE INDEX IF NOT EXISTS ix_noticia_edicion ON public.noticia (id_edicion, fecha);

-- ---------------------------------------------------------------- V43/V44, V47
ALTER TABLE public.personal_apoyo ADD COLUMN IF NOT EXISTS descripcion_tarea VARCHAR(200);
ALTER TABLE public.personal_apoyo ADD COLUMN IF NOT EXISTS foto VARCHAR(300);

-- ---------------------------------------------------------------- V45
CREATE TABLE IF NOT EXISTS public.apoyo_acceso (
    id                BIGSERIAL PRIMARY KEY,
    id_personal_apoyo BIGINT NOT NULL REFERENCES public.personal_apoyo(id),
    sentido           CHAR(1) NOT NULL CHECK (sentido IN ('E', 'S')),
    cuando            TIMESTAMP NOT NULL DEFAULT now(),
    registrado_por    BIGINT,
    origen            VARCHAR(20)
);
CREATE INDEX IF NOT EXISTS ix_apoyo_acceso_persona ON public.apoyo_acceso (id_personal_apoyo, cuando DESC, id DESC);

-- ---------------------------------------------------------------- credenciales (sin numero)
-- Entradas y salidas de los responsables por la puerta (CredencialAccesoDao).
CREATE TABLE IF NOT EXISTS public.credencial_acceso (
    id             BIGSERIAL PRIMARY KEY,
    id_responsable BIGINT NOT NULL REFERENCES public.responsable(id),
    sentido        CHAR(1) NOT NULL CHECK (sentido IN ('E', 'S')),
    cuando         TIMESTAMP NOT NULL DEFAULT now(),
    registrado_por BIGINT,
    origen         VARCHAR(20)
);
CREATE INDEX IF NOT EXISTS ix_credencial_acceso_resp ON public.credencial_acceso (id_responsable, cuando DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_credencial_acceso_cuando ON public.credencial_acceso (cuando);

-- Cada credencial generada/impresa (CredencialImpresionDao). "Emitida" = tiene al menos una fila.
CREATE TABLE IF NOT EXISTS public.credencial_impresion (
    id             BIGSERIAL PRIMARY KEY,
    id_responsable BIGINT NOT NULL REFERENCES public.responsable(id),
    id_inscripcion BIGINT REFERENCES public.inscripcion(id),
    plantilla      VARCHAR(200),
    faltantes      TEXT,
    impreso_por    BIGINT,
    impreso_en     TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_credencial_impresion_resp ON public.credencial_impresion (id_responsable);

-- ---------------------------------------------------------------- anuncios y textos del plano
CREATE TABLE IF NOT EXISTS public.anuncio (
    id                       BIGSERIAL PRIMARY KEY,
    titulo                   VARCHAR(160),
    mensaje                  VARCHAR(2000),
    nivel                    VARCHAR(10),
    vigente_hasta            TIMESTAMP,
    _fecha_registro          TIMESTAMP(6),
    _registro_id_usuario     BIGINT,
    _fecha_modificacion      TIMESTAMP(6),
    _modificacion_id_usuario BIGINT,
    _estado                  VARCHAR(1) NOT NULL DEFAULT 'A'
);

CREATE TABLE IF NOT EXISTS public.plano_texto (
    id                       BIGSERIAL PRIMARY KEY,
    id_edicion               BIGINT REFERENCES public.edicion(id),
    contenido                VARCHAR(300),
    mapa_x                   DOUBLE PRECISION,
    mapa_y                   DOUBLE PRECISION,
    tamano                   DOUBLE PRECISION,
    color                    VARCHAR(20),
    color_borde              VARCHAR(20),
    grosor_borde             DOUBLE PRECISION,
    rotacion                 DOUBLE PRECISION,
    _fecha_registro          TIMESTAMP(6),
    _registro_id_usuario     BIGINT,
    _fecha_modificacion      TIMESTAMP(6),
    _modificacion_id_usuario BIGINT,
    _estado                  VARCHAR(1) NOT NULL DEFAULT 'A'
);
CREATE INDEX IF NOT EXISTS ix_plano_texto_edicion ON public.plano_texto (id_edicion);

COMMIT;

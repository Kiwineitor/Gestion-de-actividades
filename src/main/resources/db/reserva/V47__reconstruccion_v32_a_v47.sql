BEGIN;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns
              WHERE table_schema = 'public' AND table_name = 'inscripcion'
                AND column_name = 'num_comprobante' AND data_type <> 'character varying') THEN
    ALTER TABLE public.inscripcion
      ALTER COLUMN num_comprobante TYPE varchar(100) USING num_comprobante::text;
  END IF;
END $$;

DROP INDEX IF EXISTS public.ux_vendedor_puesto_caseta;
CREATE UNIQUE INDEX IF NOT EXISTS ux_vendedor_puesto_pareja
    ON public.vendedor_puesto (id_usuario, id_puesto);

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

INSERT INTO public.area (sigla, nombre, orden, _fecha_registro, _fecha_modificacion, _estado)
SELECT v.sigla, v.sigla, v.orden, now(), now(), 'A'
  FROM (VALUES ('ACEF', 1), ('ACBN', 2), ('ACYT', 3)) AS v(sigla, orden)
 WHERE NOT EXISTS (SELECT 1 FROM public.area a WHERE upper(a.sigla) = v.sigla AND a._estado <> 'X');

ALTER TABLE public.usuario ADD COLUMN IF NOT EXISTS id_area_seguimiento BIGINT REFERENCES public.area(id);

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
CREATE UNIQUE INDEX IF NOT EXISTS ux_categoria_opcion_predeterminada
    ON public.categoria_opcion (id_categoria) WHERE predeterminada AND _estado <> 'X';

ALTER TABLE public.inscripcion_puesto
    ADD COLUMN IF NOT EXISTS id_categoria_opcion BIGINT REFERENCES public.categoria_opcion(id);

ALTER TABLE public.responsable ADD COLUMN IF NOT EXISTS es_extra BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.responsable ADD COLUMN IF NOT EXISTS monto_extra NUMERIC(10,2);
ALTER TABLE public.responsable ADD COLUMN IF NOT EXISTS comprobante_extra VARCHAR(300);

ALTER TABLE public.puesto ADD COLUMN IF NOT EXISTS precio NUMERIC(10,2);

ALTER TABLE public.noche_fexpo ADD COLUMN IF NOT EXISTS audio_archivo VARCHAR(300);

CREATE TABLE IF NOT EXISTS public.contador_visitas (
    pagina        VARCHAR(60) PRIMARY KEY,
    total         BIGINT NOT NULL DEFAULT 0,
    ultima_visita TIMESTAMP
);

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

ALTER TABLE public.personal_apoyo ADD COLUMN IF NOT EXISTS descripcion_tarea VARCHAR(200);
ALTER TABLE public.personal_apoyo ADD COLUMN IF NOT EXISTS foto VARCHAR(300);

CREATE TABLE IF NOT EXISTS public.apoyo_acceso (
    id                BIGSERIAL PRIMARY KEY,
    id_personal_apoyo BIGINT NOT NULL REFERENCES public.personal_apoyo(id),
    sentido           CHAR(1) NOT NULL CHECK (sentido IN ('E', 'S')),
    cuando            TIMESTAMP NOT NULL DEFAULT now(),
    registrado_por    BIGINT,
    origen            VARCHAR(20)
);
CREATE INDEX IF NOT EXISTS ix_apoyo_acceso_persona ON public.apoyo_acceso (id_personal_apoyo, cuando DESC, id DESC);

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

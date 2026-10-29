SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', 'public', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';

CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;

COMMENT ON EXTENSION unaccent IS 'text search dictionary that removes accents';

CREATE FUNCTION public.fn_get_inscripciones(p_usuario_id bigint) RETURNS TABLE(id_usuario bigint, id_entidad bigint, id_inscripcion bigint, nombre_entidad character varying, objeto character varying, representante_legal character varying, nit character varying, descripcion character varying, ci_representante character varying, tipo_entidad character varying, pago_contado boolean, num_comprobante text, entidad_bancaria text, img_comprobante text, categoria character varying, total_costo numeric, fecha_registro timestamp without time zone)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY
    SELECT 
        u.id                                     AS id_usuario, 
        e.id                                     AS id_entidad,
        i.id                                     AS id_inscripcion,
        e.nombre                                 AS nombre_entidad, 
        e.objeto, 
        e.representante_legal, 
        e.nit, 
        e.descripcion, 
        e.ci_representante,
        te.nombre                                AS tipo_entidad,
        i.pago_contado,
        COALESCE(i.num_comprobante::text, 'SIN DATO')                  AS num_comprobante,
        COALESCE(NULLIF(i.entidad_bancaria, ''), 'SIN DATO')           AS entidad_bancaria,
        COALESCE(NULLIF(i.img_comprobante, ''), 'SIN DATO')            AS img_comprobante,
        c.nombre                                AS categoria,
        SUM(ip.costo)                            AS total_costo,
        i."_fecha_registro"                      AS fecha_registro
    FROM usuario u
    INNER JOIN entidad e         ON e."_registro_id_usuario" = u.id
    INNER JOIN tipo_entidad te   ON te.id = e.id_tipo_entidad
    INNER JOIN inscripcion i     ON i.id_entidad = e.id
    INNER JOIN inscripcion_puesto ip ON ip.id_inscripcion = i.id
    INNER JOIN puesto p          ON p.id = ip.id_puesto
    INNER JOIN categoria c       ON c.id = p.id_categoria
    WHERE 
        u.id = p_usuario_id
        AND u."_estado" <> 'X'
        AND e."_estado" <> 'X'
        AND te."_estado" <> 'X'
        AND i."_estado" <> 'X'
        AND ip."_estado" <> 'X'
        AND p."_estado" <> 'X'
        AND c."_estado" <> 'X'
        AND p.estado_puesto = 'O'
    GROUP BY
        u.id, 
        e.id,
        i.id,
        e.nombre, 
        e.objeto, 
        e.representante_legal, 
        e.nit, 
        e.descripcion, 
        e.ci_representante,
        te.nombre,
        i.pago_contado,
        i.num_comprobante,
        i.entidad_bancaria,
        i.img_comprobante,
        c.nombre,
        i."_fecha_registro"
    ORDER BY i."_fecha_registro" DESC;
END;
$$;

CREATE FUNCTION public.fn_inscripciones_por_categoria() RETURNS TABLE(id_categoria bigint, id_inscripcion bigint, nombre_entidad character varying, representante_legal character varying)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY
    SELECT 
		c.id,
        i2.id, 
        e.nombre, 
        e.representante_legal
    FROM inscripcion_puesto i
    INNER JOIN inscripcion i2 ON i2.id = i.id_inscripcion
    INNER JOIN entidad e ON e.id = i2.id_entidad
    INNER JOIN puesto p ON p.id = i.id_puesto
    INNER JOIN categoria c ON c.id = p.id_categoria
    WHERE i._estado <> 'X' 
      AND i2._estado <> 'X' 
      AND e._estado <> 'X' 
      AND p._estado <> 'X' 
      AND c._estado <> 'X' 
      AND p.estado_puesto = 'O'
    GROUP BY i2.id, e.nombre, e.representante_legal, c.id
    ORDER BY e.representante_legal ASC;
END;
$$;

CREATE FUNCTION public.fn_lista_puestos() RETURNS TABLE(codigo integer, tamano character varying, categoria character varying, estado_puesto character varying)
    LANGUAGE plpgsql
    AS $$
begin
    return query
    select p.codigo::integer,
           p.tamano,
           c.nombre,
           p.estado_puesto
    from puesto p
    inner join categoria c on c.id = p.id_categoria
    where p.estado_puesto <> 'X' and c.id not in (1,4,8,10,13,14,15,16,17,20,21,22,24,25) 
    order by c.nombre, p.codigo::integer;
end;
$$;

CREATE FUNCTION public.fn_norm_text(p_text text) RETURNS text
    LANGUAGE sql IMMUTABLE
    AS $$
  SELECT regexp_replace(public.unaccent(lower(coalesce(p_text,''))), '\s+', ' ', 'g')::text
$$;

CREATE FUNCTION public.fn_norm_fullname(p_nombre text, p_paterno text, p_materno text) RETURNS text
    LANGUAGE sql IMMUTABLE
    AS $$
  SELECT trim(both ' ' FROM public.fn_norm_text(
           trim(both ' ' FROM coalesce(p_nombre,'') || ' ' ||
                           coalesce(p_paterno,'') || ' ' ||
                           coalesce(p_materno,''))))
$$;

CREATE FUNCTION public.fn_sort_words(p text) RETURNS text
    LANGUAGE sql IMMUTABLE
    AS $$
  WITH toks AS (
    SELECT unnest(regexp_split_to_array(public.fn_norm_text(p), '\s+')) AS w
  )
  SELECT string_agg(w, ' ' ORDER BY w)
  FROM toks
$$;

CREATE FUNCTION public.obtener_inscripcion_detalle(p_id_inscripcion bigint DEFAULT NULL::bigint) RETURNS TABLE(id bigint, fecha_compra timestamp without time zone, nombre_entidad character varying, representante_legal character varying, ci_representante character varying, ci_persona character varying, nit character varying, descripcion character varying, objeto character varying, nombre_persona character varying, paterno_persona character varying, materno_persona character varying, datoqr text, foto_persona character varying)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY
    SELECT 
        i.id, 
        i.fecha_compra, 
        e.nombre, 
        e.representante_legal, 
        e.ci_representante,
		p.ci, 
        e.nit, 
        e.descripcion, 
        e.objeto,
        p.nombre,
        p.paterno,
        p.materno,
        (
            'RESPONSABLE: '||p.nombre|| E'\n' ||
            'CI: '||p.ci|| E'\n' ||
            'ENTIDAD: '||e.nombre
        ) AS datoqr,
		p.foto
    FROM inscripcion i 
    INNER JOIN inscripcion_puesto i2 ON i2.id_inscripcion = i.id
    INNER JOIN entidad e ON e.id = i.id_entidad
    INNER JOIN responsable r ON r.id_entidad = e.id
    INNER JOIN persona p ON p.id = r.id_persona
    WHERE 
        i._estado <> 'X' 
        AND i2._estado <> 'X' 
        AND e._estado <> 'X'
        AND i.id = p_id_inscripcion
	GROUP BY 
		i.id, 
        i.fecha_compra, 
        e.nombre, 
        e.representante_legal, 
        e.ci_representante,
		p.ci, 
        e.nit, 
        e.descripcion, 
        e.objeto,
        p.nombre,
        p.paterno,
        p.materno,
        datoqr,
		p.foto;
END;
$$;

CREATE FUNCTION public.obtener_puestos_libres(p_id_categoria bigint DEFAULT NULL::bigint) RETURNS TABLE(id bigint, codigo character varying, tamano character varying, categoria_id bigint, categoria_nombre character varying)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY
    SELECT p.id, p.codigo, p.tamano, c.id, c.nombre
    FROM puesto p
    JOIN categoria c ON c.id = p.id_categoria
    WHERE p.estado_puesto = 'L'
      AND (p_id_categoria IS NULL OR c.id = p_id_categoria)
    ORDER BY p.codigo ASC;
END;
$$;

CREATE FUNCTION public.obtener_puestos_por_inscripcion(p_id_inscripcion bigint) RETURNS TABLE(codigo character varying, tamano character varying, costo numeric)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY
    SELECT 
        p.codigo, 
        p.tamano, 
        ip.costo
    FROM inscripcion_puesto ip
    INNER JOIN puesto p ON p.id = ip.id_puesto
    WHERE ip.id_inscripcion = p_id_inscripcion
    ORDER BY p.codigo ASC;
END;
$$;

CREATE FUNCTION public.obtenercostopuesto(p_id_tipo_entidad bigint, p_tamano_puesto character varying, p_id_categoria bigint) RETURNS numeric
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_precio_categoria NUMERIC(10,2);
    v_costo            NUMERIC(10,2);
    v_tam              TEXT;
BEGIN
    v_tam := replace(lower(trim(p_tamano_puesto)), ' ', '');

	IF p_id_categoria = 9 THEN
        RETURN 0.00;
    END IF;

    IF p_id_categoria IS NOT NULL THEN
        SELECT c.precio_base
          INTO v_precio_categoria
          FROM public.categoria c
         WHERE c.id = p_id_categoria;

        IF COALESCE(v_precio_categoria, 0) > 0 THEN
            RETURN v_precio_categoria;
        END IF;
    END IF;

    v_costo := CASE
        WHEN v_tam = '3x3' AND p_id_tipo_entidad IN (1, 2, 4) THEN  50.00
        WHEN v_tam = '3x3' AND p_id_tipo_entidad = 3              THEN 100.00
        WHEN v_tam = '3x3' AND p_id_tipo_entidad = 5              THEN 200.00
        ELSE 0.00
    END;

    RETURN v_costo;
END;
$$;

CREATE FUNCTION public.verificar_duplicado_id_puesto() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM inscripcion_puesto
        WHERE id_puesto = NEW.id_puesto
          AND id <> NEW.id
    ) THEN
        RAISE EXCEPTION 'Ya existe un registro con id_puesto=%', NEW.id_puesto;
    END IF;

    RETURN NEW;
END;
$$;

CREATE FUNCTION public.verificar_inscripcion_puesto(p_ci text, p_id_entidad integer) RETURNS integer
    LANGUAGE plpgsql
    AS $$
declare
    v_result integer;
begin
    select case 
               when exists (
                   select 1
                   from inscripcion_puesto i 
                   inner join inscripcion i2 on i2.id = i.id_inscripcion
                   inner join responsable r on r.id_entidad = i2.id_entidad
                   inner join persona p on p.id = r.id_persona
                   where 
                       i._estado <> 'X'
                       and i2._estado <> 'X'
                       and p.ci = p_ci
                       and i2.id_entidad = p_id_entidad
                   limit 1
               ) 
               then 1 
               else 0 
           end
    into v_result;

    return v_result;
end;
$$;

SET default_tablespace = '';

SET default_table_access_method = heap;

CREATE TABLE public.admistrativo (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    id_cargo bigint,
    id_oficina bigint,
    id_persona bigint,
    _estado character varying(255),
    codigo_funcionario character varying(255)
);

ALTER TABLE public.admistrativo ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.admistrativo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.cargo (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id_cargo bigint NOT NULL,
    _estado character varying(255),
    descripcion character varying(255),
    nombre character varying(255)
);

ALTER TABLE public.cargo ALTER COLUMN id_cargo ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.cargo_id_cargo_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.categoria (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    _estado character varying(255),
    color character varying(255),
    descripcion character varying(255),
    nombre character varying(255),
    precio_base numeric(10,2) DEFAULT 0
);

ALTER TABLE public.categoria ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.categoria_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.categoria_venta (
    id_categoria_venta bigint NOT NULL,
    _estado character varying(255),
    _fecha_modificacion timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _fecha_registro timestamp(6) without time zone,
    _registro_id_usuario bigint,
    nombre character varying(255),
    precio character varying(255)
);

ALTER TABLE public.categoria_venta ALTER COLUMN id_categoria_venta ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.categoria_venta_id_categoria_venta_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.control_acceso_responsable (
    id bigint NOT NULL,
    _estado character varying(255),
    _fecha_modificacion timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _fecha_registro timestamp(6) without time zone,
    _registro_id_usuario bigint,
    ci character varying(255),
    _creado_en timestamp(6) without time zone,
    fecha_entrada timestamp(6) without time zone,
    fecha_salida timestamp(6) without time zone,
    id_persona bigint,
    observacion character varying(255)
);

ALTER TABLE public.control_acceso_responsable ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.control_acceso_responsable_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.control_responsable (
    id bigint NOT NULL,
    _estado character varying(255),
    estado_actual character varying(255),
    evento character varying(255),
    fecha_evento timestamp(6) without time zone,
    id_responsable bigint,
    observacion character varying(255)
);

ALTER TABLE public.control_responsable ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.control_responsable_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.entidad (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    id_tipo_entidad bigint,
    _estado character varying(255),
    ci_representante character varying(255),
    descripcion character varying(255),
    nit character varying(255),
    nombre character varying(255),
    objeto character varying(255),
    representante_legal character varying(255)
);

ALTER TABLE public.entidad ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.entidad_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.inscripcion (
    pago_contado boolean NOT NULL,
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    fecha_compra timestamp(6) without time zone,
    fecha_fin timestamp(6) without time zone,
    fecha_inicio timestamp(6) without time zone,
    id bigint NOT NULL,
    id_entidad bigint,
    num_comprobante bigint,
    _estado character varying(255),
    entidad_bancaria character varying(255),
    img_comprobante character varying(255),
    inscripcion_estado character varying(255)
);

ALTER TABLE public.inscripcion ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.inscripcion_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.inscripcion_puesto (
    costo numeric(38,2),
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    id_inscripcion bigint,
    id_puesto bigint,
    _estado character varying(255)
);

ALTER TABLE public.inscripcion_puesto ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.inscripcion_puesto_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.oficina (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    _estado character varying(255),
    nombre character varying(255)
);

ALTER TABLE public.oficina ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.oficina_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.persona (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    _estado character varying(255),
    celular character varying(255),
    ci character varying(255),
    correo character varying(255),
    foto character varying(255),
    materno character varying(255),
    nombre character varying(255),
    paterno character varying(255)
);

ALTER TABLE public.persona ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.persona_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.puesto (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    id_categoria bigint,
    _estado character varying(255),
    codigo character varying(255),
    estado_puesto character varying(255),
    tamano character varying(255)
);

ALTER TABLE public.puesto ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.puesto_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.responsable (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    id_entidad bigint,
    id_persona bigint,
    _estado character varying(255)
);

ALTER TABLE public.responsable ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.responsable_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.rol (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    _estado character varying(255),
    descripcion character varying(255),
    nombre character varying(255)
);

ALTER TABLE public.rol ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.rol_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.tipo_entidad (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    _estado character varying(255),
    nombre character varying(255)
);

ALTER TABLE public.tipo_entidad ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.tipo_entidad_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.usuario (
    _fecha_modificacion timestamp(6) without time zone,
    _fecha_registro timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _registro_id_usuario bigint,
    id bigint NOT NULL,
    persona_id bigint NOT NULL,
    rol_id bigint NOT NULL,
    username character varying(50) NOT NULL,
    password character varying(120) NOT NULL,
    _estado character varying(255)
);

ALTER TABLE public.usuario ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.usuario_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE public.venta_boleto (
    id bigint NOT NULL,
    _estado character varying(255),
    _fecha_modificacion timestamp(6) without time zone,
    _modificacion_id_usuario bigint,
    _fecha_registro timestamp(6) without time zone,
    _registro_id_usuario bigint,
    cantidad integer NOT NULL,
    fecha_hora timestamp(6) without time zone NOT NULL,
    precio_unitario numeric(12,2) NOT NULL,
    total numeric(14,2) NOT NULL,
    categoria_id bigint NOT NULL,
    persona_id bigint NOT NULL
);

ALTER TABLE public.venta_boleto ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.venta_boleto_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

ALTER TABLE ONLY public.admistrativo
    ADD CONSTRAINT admistrativo_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.cargo
    ADD CONSTRAINT cargo_pkey PRIMARY KEY (id_cargo);

ALTER TABLE ONLY public.categoria
    ADD CONSTRAINT categoria_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.categoria_venta
    ADD CONSTRAINT categoria_venta_pkey PRIMARY KEY (id_categoria_venta);

ALTER TABLE ONLY public.control_acceso_responsable
    ADD CONSTRAINT control_acceso_responsable_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.control_responsable
    ADD CONSTRAINT control_responsable_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.entidad
    ADD CONSTRAINT entidad_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.inscripcion
    ADD CONSTRAINT inscripcion_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.inscripcion_puesto
    ADD CONSTRAINT inscripcion_puesto_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.oficina
    ADD CONSTRAINT oficina_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.persona
    ADD CONSTRAINT persona_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.puesto
    ADD CONSTRAINT puesto_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.responsable
    ADD CONSTRAINT responsable_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.rol
    ADD CONSTRAINT rol_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.tipo_entidad
    ADD CONSTRAINT tipo_entidad_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT usuario_username_key UNIQUE (username);

ALTER TABLE ONLY public.venta_boleto
    ADD CONSTRAINT venta_boleto_pkey PRIMARY KEY (id);

CREATE INDEX idx_persona_fullname_trgm ON public.persona USING gin (public.fn_norm_fullname((nombre)::text, (paterno)::text, (materno)::text) public.gin_trgm_ops);

CREATE TRIGGER trg_no_duplicados_id_puesto BEFORE INSERT OR UPDATE ON public.inscripcion_puesto FOR EACH ROW EXECUTE FUNCTION public.verificar_duplicado_id_puesto();

ALTER TABLE ONLY public.admistrativo
    ADD CONSTRAINT fk1ilj5jwr4gsjdfa4mn6y9b26d FOREIGN KEY (id_cargo) REFERENCES public.cargo(id_cargo);

ALTER TABLE ONLY public.admistrativo
    ADD CONSTRAINT fk2pagciuthecvved7xmm5oj1iy FOREIGN KEY (id_oficina) REFERENCES public.oficina(id);

ALTER TABLE ONLY public.inscripcion
    ADD CONSTRAINT fkc34jloi4md3jw4mj9fcmqlrpy FOREIGN KEY (id_entidad) REFERENCES public.entidad(id);

ALTER TABLE ONLY public.admistrativo
    ADD CONSTRAINT fkd51756me5gd89eerf6exa02f4 FOREIGN KEY (id_persona) REFERENCES public.persona(id);

ALTER TABLE ONLY public.responsable
    ADD CONSTRAINT fkg6ik67qqypimgevl771ekaj2 FOREIGN KEY (id_persona) REFERENCES public.persona(id);

ALTER TABLE ONLY public.responsable
    ADD CONSTRAINT fkhqnwk1ef6ctamp9xdhevl43rw FOREIGN KEY (id_entidad) REFERENCES public.entidad(id);

ALTER TABLE ONLY public.venta_boleto
    ADD CONSTRAINT fkhr4k12yydrb7mkr1akttowki9 FOREIGN KEY (categoria_id) REFERENCES public.categoria_venta(id_categoria_venta);

ALTER TABLE ONLY public.venta_boleto
    ADD CONSTRAINT fki5tnxbyavwha7ss5amm1g5n6i FOREIGN KEY (persona_id) REFERENCES public.persona(id);

ALTER TABLE ONLY public.puesto
    ADD CONSTRAINT fkisjt5ecadohyxbvm2n8h0v5x6 FOREIGN KEY (id_categoria) REFERENCES public.categoria(id);

ALTER TABLE ONLY public.entidad
    ADD CONSTRAINT fkjxan3wlyg8ruqw2h9ufunnn6c FOREIGN KEY (id_tipo_entidad) REFERENCES public.tipo_entidad(id);

ALTER TABLE ONLY public.inscripcion_puesto
    ADD CONSTRAINT fkkfam8gl3w4pfnsieufylijvh5 FOREIGN KEY (id_inscripcion) REFERENCES public.inscripcion(id);

ALTER TABLE ONLY public.inscripcion_puesto
    ADD CONSTRAINT fklbny80abvdrud0vcekflwmj0a FOREIGN KEY (id_puesto) REFERENCES public.puesto(id);

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT fklse7lqghmt3r1sp298ss9s5bc FOREIGN KEY (persona_id) REFERENCES public.persona(id);

ALTER TABLE ONLY public.usuario
    ADD CONSTRAINT fkshkwj12wg6vkm6iuwhvcfpct8 FOREIGN KEY (rol_id) REFERENCES public.rol(id);

ALTER TABLE ONLY public.inscripcion
    ADD CONSTRAINT fkt4svkp4s76su1ehfiddnhit2c FOREIGN KEY (_registro_id_usuario) REFERENCES public.usuario(id);


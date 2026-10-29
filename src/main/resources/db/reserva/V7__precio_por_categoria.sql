UPDATE categoria SET precio_base = 0 WHERE precio_base IS NULL;

ALTER TABLE categoria ALTER COLUMN precio_base SET DEFAULT 0;
ALTER TABLE categoria ALTER COLUMN precio_base SET NOT NULL;

UPDATE puesto SET tamano = '3x3' WHERE tamano IS NULL OR btrim(tamano) = '';

CREATE OR REPLACE FUNCTION public.obtenercostopuesto(
    p_id_tipo_entidad bigint,
    p_tamano_puesto character varying,
    p_id_categoria bigint)
  RETURNS numeric
  LANGUAGE plpgsql
AS $function$
DECLARE
    v_precio_categoria NUMERIC(10,2);
BEGIN
    IF p_id_categoria IS NULL THEN
        RETURN 0.00;
    END IF;

    SELECT c.precio_base INTO v_precio_categoria
      FROM public.categoria c
     WHERE c.id = p_id_categoria;

    RETURN COALESCE(v_precio_categoria, 0.00);
END;
$function$;

DO $$
DECLARE
    n bigint;
BEGIN
    SELECT count(*) INTO n FROM categoria WHERE precio_base IS NULL;
    IF n > 0 THEN RAISE EXCEPTION 'Quedan % categorias sin precio', n; END IF;

    SELECT count(*) INTO n FROM puesto WHERE tamano IS NULL OR btrim(tamano) = '';
    IF n > 0 THEN RAISE EXCEPTION 'Quedan % casetas sin tamaño', n; END IF;
END $$;

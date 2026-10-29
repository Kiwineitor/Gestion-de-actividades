CREATE OR REPLACE FUNCTION public.verificar_duplicado_id_puesto()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
DECLARE
    v_estado_inscripcion text;
BEGIN
    IF NEW.id_inscripcion IS NOT NULL THEN
        SELECT i._estado INTO v_estado_inscripcion
        FROM inscripcion i
        WHERE i.id = NEW.id_inscripcion;

        IF v_estado_inscripcion IS NOT NULL AND v_estado_inscripcion = 'X' THEN
            RETURN NEW;
        END IF;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM inscripcion_puesto ip
        JOIN inscripcion i ON i.id = ip.id_inscripcion
        WHERE ip.id_puesto = NEW.id_puesto
          AND ip.id <> NEW.id
          AND (i._estado IS NULL OR i._estado <> 'X')
    ) THEN
        RAISE EXCEPTION 'Ya existe un registro con id_puesto=%', NEW.id_puesto;
    END IF;

    RETURN NEW;
END;
$function$

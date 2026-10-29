CREATE TABLE IF NOT EXISTS public.edicion (
    id     bigserial PRIMARY KEY,
    nombre varchar(120) NOT NULL,
    anio   integer NOT NULL,
    activa boolean NOT NULL DEFAULT false
);

INSERT INTO public.edicion (id, nombre, anio, activa)
SELECT 1, 'FEXPO 2025', 2025, false
WHERE NOT EXISTS (SELECT 1 FROM public.edicion WHERE id = 1);

INSERT INTO public.edicion (id, nombre, anio, activa)
SELECT 2, 'FEXPO 2026', 2026, true
WHERE NOT EXISTS (SELECT 1 FROM public.edicion WHERE id = 2);

SELECT setval(pg_get_serial_sequence('public.edicion', 'id'), (SELECT max(id) FROM public.edicion), true);

ALTER TABLE public.inscripcion ADD COLUMN IF NOT EXISTS id_edicion bigint;
UPDATE public.inscripcion SET id_edicion = 1 WHERE id_edicion IS NULL;
ALTER TABLE public.inscripcion ALTER COLUMN id_edicion SET DEFAULT 2;

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inscripcion_edicion') THEN
    ALTER TABLE public.inscripcion
      ADD CONSTRAINT fk_inscripcion_edicion FOREIGN KEY (id_edicion) REFERENCES public.edicion(id);
  END IF;
END $$;

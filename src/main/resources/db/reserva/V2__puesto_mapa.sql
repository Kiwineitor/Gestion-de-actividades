ALTER TABLE public.puesto
    ADD COLUMN IF NOT EXISTS mapa_x double precision,
    ADD COLUMN IF NOT EXISTS mapa_y double precision;

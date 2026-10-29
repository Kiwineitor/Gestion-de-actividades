ALTER TABLE categoria ADD COLUMN IF NOT EXISTS tamano_mapa double precision;
ALTER TABLE puesto    ADD COLUMN IF NOT EXISTS mapa_escala double precision;

UPDATE categoria SET tamano_mapa = 0.012 WHERE tamano_mapa IS NULL;
UPDATE puesto    SET mapa_escala = 1     WHERE mapa_escala IS NULL;

ALTER TABLE categoria ALTER COLUMN tamano_mapa SET DEFAULT 0.012;
ALTER TABLE puesto    ALTER COLUMN mapa_escala SET DEFAULT 1;

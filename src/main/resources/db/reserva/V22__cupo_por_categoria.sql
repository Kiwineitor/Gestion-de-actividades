BEGIN;

ALTER TABLE vendedor_categoria ADD COLUMN IF NOT EXISTS cupo INTEGER;

COMMENT ON COLUMN vendedor_categoria.cupo IS
    'Maximo de casetas de esta categoria que el vendedor puede VENDER en la edicion activa. NULL = sin limite. No limita cuantas ve, solo cuantas coloca.';

ALTER TABLE vendedor_categoria DROP CONSTRAINT IF EXISTS ck_vendedor_categoria_cupo;
ALTER TABLE vendedor_categoria ADD CONSTRAINT ck_vendedor_categoria_cupo
    CHECK (cupo IS NULL OR cupo >= 0);

COMMIT;

ALTER TABLE inscripcion ADD COLUMN IF NOT EXISTS nota_codigo     VARCHAR(40);
ALTER TABLE inscripcion ADD COLUMN IF NOT EXISTS nota_emitida_en TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS ux_inscripcion_nota_codigo
    ON inscripcion (nota_codigo) WHERE nota_codigo IS NOT NULL;

COMMENT ON COLUMN inscripcion.nota_codigo     IS 'Codigo verificable de la nota de venta, firmado con HMAC. Se emite una vez y no cambia entre reimpresiones';
COMMENT ON COLUMN inscripcion.nota_emitida_en IS 'Cuando se emitio la nota por primera vez. Es la fecha que se imprime, no la del momento de reimprimir';

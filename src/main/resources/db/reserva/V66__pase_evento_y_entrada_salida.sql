BEGIN;

ALTER TABLE plantilla_credencial ADD COLUMN IF NOT EXISTS foto_forma VARCHAR(10);

ALTER TABLE mesa_evento ADD COLUMN IF NOT EXISTS logo_archivo VARCHAR(300);

ALTER TABLE mesa_pase ADD COLUMN IF NOT EXISTS nombre VARCHAR(160);
ALTER TABLE mesa_pase ADD COLUMN IF NOT EXISTS ci VARCHAR(20);
ALTER TABLE mesa_pase ADD COLUMN IF NOT EXISTS foto VARCHAR(300);
ALTER TABLE mesa_pase ADD COLUMN IF NOT EXISTS dentro BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS mesa_pase_acceso (
    id             BIGSERIAL PRIMARY KEY,
    id_pase        BIGINT    NOT NULL REFERENCES mesa_pase(id),
    sentido        CHAR(1)   NOT NULL CHECK (sentido IN ('E', 'S')),
    cuando         TIMESTAMP NOT NULL DEFAULT now(),
    registrado_por BIGINT
);

CREATE INDEX IF NOT EXISTS ix_mesa_pase_acceso_pase ON mesa_pase_acceso (id_pase, cuando DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_mesa_pase_acceso_cuando ON mesa_pase_acceso (cuando);

INSERT INTO mesa_pase_acceso (id_pase, sentido, cuando, registrado_por)
SELECT p.id, 'E', p.ingreso_en, p.ingreso_por
  FROM mesa_pase p
 WHERE p.ingreso_en IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM mesa_pase_acceso a WHERE a.id_pase = p.id);

UPDATE mesa_pase p SET dentro = TRUE
 WHERE p.ingreso_en IS NOT NULL
   AND (SELECT a.sentido FROM mesa_pase_acceso a WHERE a.id_pase = p.id ORDER BY a.cuando DESC, a.id DESC LIMIT 1) = 'E';

INSERT INTO plantilla_credencial (codigo, etiqueta, detalle, imagen, requiere_foto, centrado, mayusculas,
                                  foto_circular, multilinea, color_texto, tipo_texto, cajas, orden,
                                  predeterminada, uso, foto_forma)
SELECT 'PASE_EVENTO', 'Pase de evento',
       'Eventos con mesas: foto de la persona (o logo del local) en rombo, nombre grande y QR de entrada y salida',
       'static/assets/credencial-pase-evento.png', FALSE, TRUE, TRUE, FALSE, TRUE, '#FFFFFF', 'PASE',
       '{"qr":{"x":0.5656,"y":0.7062,"ancho":0.3472,"alto":0.2671},'
       || '"foto":{"x":0.2701,"y":0.0599,"ancho":0.4598,"alto":0.3537},'
       || '"nombre":{"x":0.06,"y":0.452,"ancho":0.88,"alto":0.13},'
       || '"tipo":{"x":0.08,"y":0.588,"ancho":0.84,"alto":0.045},'
       || '"empresa":{"x":0.06,"y":0.637,"ancho":0.88,"alto":0.05},'
       || '"codigo":{"x":0.06,"y":0.722,"ancho":0.44,"alto":0.075},'
       || '"zona":{"x":0.06,"y":0.848,"ancho":0.44,"alto":0.05}}',
       (SELECT COALESCE(max(orden), -1) + 1 FROM plantilla_credencial), FALSE, 'EXPOSITOR', 'ROMBO'
 WHERE NOT EXISTS (SELECT 1 FROM plantilla_credencial WHERE codigo = 'PASE_EVENTO');

COMMIT;

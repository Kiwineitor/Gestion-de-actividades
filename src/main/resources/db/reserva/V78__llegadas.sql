CREATE TABLE IF NOT EXISTS llegada (
    id BIGSERIAL PRIMARY KEY,
    id_inscripcion BIGINT NOT NULL REFERENCES inscripcion(id),
    dia DATE NOT NULL DEFAULT CURRENT_DATE,
    llego_en TIMESTAMP NOT NULL DEFAULT now(),
    registrado_por BIGINT,
    personas INTEGER,
    nota VARCHAR(300),
    CONSTRAINT llegada_una_por_dia UNIQUE (id_inscripcion, dia)
);

CREATE INDEX IF NOT EXISTS llegada_dia ON llegada (dia);

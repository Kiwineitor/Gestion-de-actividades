ALTER TABLE public.puesto
    ADD COLUMN IF NOT EXISTS reservado_por_id_usuario bigint,
    ADD COLUMN IF NOT EXISTS reserva_expira          timestamp without time zone,
    ADD COLUMN IF NOT EXISTS version                 bigint NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_puesto_reserva_expira
    ON public.puesto (reserva_expira)
    WHERE estado_puesto = 'T';

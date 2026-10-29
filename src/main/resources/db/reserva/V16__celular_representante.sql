ALTER TABLE entidad ADD COLUMN IF NOT EXISTS celular_representante varchar(30);

COMMENT ON COLUMN entidad.celular_representante IS
  'Telefono del responsable legal (el dueño). Obligatorio en altas nuevas desde la SPA.';

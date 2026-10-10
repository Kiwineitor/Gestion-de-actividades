#!/usr/bin/env bash
# Carga los datos de prueba si CARGAR_DATOS_PRUEBA=true (por defecto lo esta en
# docker-compose.yml). Con false queda una instalacion limpia: solo esquema, roles y,
# al arrancar Java, admin1/admin2.
set -euo pipefail

if [ "${CARGAR_DATOS_PRUEBA:-false}" != "true" ]; then
  echo "== UniFex: CARGAR_DATOS_PRUEBA != true, base sin datos de prueba =="
  exit 0
fi

echo "== UniFex: cargando datos de prueba =="
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f /unifex/datos-prueba.sql

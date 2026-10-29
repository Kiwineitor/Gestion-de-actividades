#!/usr/bin/env bash

set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

crear_tabla_control() {
  psql -v ON_ERROR_STOP=1 -c "
    create table if not exists public._migraciones_aplicadas (
      nombre       text primary key,
      aplicada_en  timestamptz not null default now()
    );" >/dev/null
}

if [ "${1:-}" = "--estado" ]; then
  crear_tabla_control
  psql -c "select nombre, aplicada_en from public._migraciones_aplicadas order by aplicada_en;"
  exit 0
fi

if [ "${1:-}" = "--marcar-aplicada" ]; then
  nombre="${2:?Uso: --marcar-aplicada NOMBRE_DEL_ARCHIVO.sql}"
  if [ ! -f "$DIR/$nombre" ]; then
    echo "No existe $DIR/$nombre" >&2
    exit 1
  fi
  crear_tabla_control
  psql -v ON_ERROR_STOP=1 -c "
    insert into public._migraciones_aplicadas(nombre) values ('$nombre')
    on conflict (nombre) do nothing;"
  echo "Marcada como ya aplicada (sin ejecutar): $nombre"
  exit 0
fi

echo "== Verificando conexion a $(psql -tAc 'select current_database()') =="
crear_tabla_control

aplicadas=0
saltadas=0
while IFS= read -r archivo; do
  nombre="$(basename "$archivo")"
  ya=$(psql -tAc "select 1 from public._migraciones_aplicadas where nombre = '$nombre'")
  if [ "$ya" = "1" ]; then
    echo "-- $nombre ya aplicada, se salta"
    saltadas=$((saltadas + 1))
    continue
  fi
  echo "== Aplicando $nombre =="
  psql -v ON_ERROR_STOP=1 -f "$archivo"
  psql -v ON_ERROR_STOP=1 -c "insert into public._migraciones_aplicadas(nombre) values ('$nombre');" >/dev/null
  echo "-- $nombre aplicada y registrada"
  aplicadas=$((aplicadas + 1))
done < <(printf '%s\n' "$DIR"/V*.sql | sort -V)

echo "== Listo: $aplicadas aplicadas, $saltadas ya estaban =="

#!/usr/bin/env bash
# Construye el esquema completo de UniFex (V0..V49) sobre la base recien creada.
# Lo ejecuta la imagen oficial de postgres UNA sola vez, con el volumen vacio.
set -euo pipefail

# Durante la inicializacion el servidor solo escucha por socket local: psql sin host.
export PGUSER="$POSTGRES_USER"
export PGDATABASE="$POSTGRES_DB"
unset PGHOST PGPORT

echo "== UniFex: aplicando migraciones de esquema =="
bash /unifex/migraciones/aplicar-migraciones.sh

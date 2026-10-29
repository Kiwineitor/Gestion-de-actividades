#!/usr/bin/env bash
set -euo pipefail

export PGUSER="$POSTGRES_USER"
export PGDATABASE="$POSTGRES_DB"
unset PGHOST PGPORT

echo "== UniFex: aplicando migraciones de esquema =="
bash /unifex/migraciones/aplicar-migraciones.sh

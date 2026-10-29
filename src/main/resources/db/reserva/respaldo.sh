#!/usr/bin/env bash

set -euo pipefail

DIR_RESPALDOS="${UNIFEX_RESPALDOS:-$HOME/unifex-respaldos}"
mkdir -p "$DIR_RESPALDOS"

base_actual() { psql -tAc 'select current_database()'; }

case "${1:-}" in
  crear)
    ETIQUETA="${2:-manual}"
    BASE="$(base_actual)"
    ARCHIVO="$DIR_RESPALDOS/${BASE}_$(date +%Y%m%d-%H%M%S)_${ETIQUETA}.dump"
    echo "== Respaldando $BASE en $ARCHIVO =="
    pg_dump -Fc --no-owner --no-privileges -f "$ARCHIVO"
    if pg_restore --list "$ARCHIVO" >/dev/null 2>&1; then
      echo "== Listo: $(du -h "$ARCHIVO" | cut -f1) — verificado, se puede restaurar =="
    else
      echo "!! El archivo se creo pero pg_restore NO puede leerlo. NO te fies de el." >&2
      exit 1
    fi
    ;;

  listar)
    echo "== Respaldos en $DIR_RESPALDOS =="
    ls -lht "$DIR_RESPALDOS"/*.dump 2>/dev/null | awk '{print "  " $5 "\t" $6, $7, $8 "\t" $9}' \
      || echo "  (todavia no hay ninguno)"
    ;;

  restaurar)
    ARCHIVO="${2:?Uso: ./respaldo.sh restaurar <archivo.dump>}"
    [ -f "$ARCHIVO" ] || { echo "No existe $ARCHIVO" >&2; exit 1; }
    BASE="$(base_actual)"
    echo "⚠ Esto va a DEJAR la base '$BASE' exactamente como estaba en:"
    echo "    $ARCHIVO"
    echo "  Todo lo que haya pasado despues se pierde."
    read -r -p "  Escribe el nombre de la base para confirmar: " CONFIRMA
    [ "$CONFIRMA" = "$BASE" ] || { echo "Cancelado."; exit 1; }
    pg_restore --clean --if-exists --no-owner --no-privileges -d "$BASE" "$ARCHIVO"
    echo "== Restaurado =="
    ;;

  *)
    sed -n '2,32p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac

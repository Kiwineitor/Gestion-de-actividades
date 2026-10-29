#!/usr/bin/env bash

set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

BASE="http://10.0.2.2:7676"
INSTALAR=false
for arg in "$@"; do
  case "$arg" in
    --instalar) INSTALAR=true ;;
    http://*|https://*) BASE="$arg" ;;
    *) echo "Argumento no reconocido: $arg" >&2; exit 1 ;;
  esac
done

echo "== Compilando con VITE_API_BASE=$BASE =="
VITE_API_BASE="$BASE" npm run build

echo "== Verificando que la IP quedo horneada en el bundle =="
if ! grep -qo "${BASE#http://}" dist/assets/index-*.js 2>/dev/null; then
  echo "AVISO: no encontre '$BASE' en el bundle compilado. Revisa antes de seguir." >&2
fi

echo "== Sincronizando con el proyecto Android =="
npx cap sync android

echo "== Generando APK debug =="
(cd android && chmod +x gradlew && ./gradlew assembleDebug)

APK="android/app/build/outputs/apk/debug/app-debug.apk"
echo "== Listo: $APK =="

if $INSTALAR; then
  ADB="$HOME/Android/Sdk/platform-tools/adb"
  [ -x "$ADB" ] || ADB="adb"
  echo "== Reinstalando en el dispositivo/emulador conectado (por si quedo cache vieja) =="
  "$ADB" uninstall bo.edu.uap.unifex || true
  "$ADB" install "$APK"
fi

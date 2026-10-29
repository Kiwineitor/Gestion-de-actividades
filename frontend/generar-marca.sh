#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

LOGO=imagenes/logoFexpoUapV2.png       # el logotipo apaisado, sobre fondo negro opaco
ICONO=imagenes/iconoFexpoUapV2Apk.png  # el badge cuadrado, pensado como icono de app
RES=android/app/src/main/res
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT

command -v convert >/dev/null || { echo "Falta ImageMagick (convert)"; exit 1; }

convert "$LOGO"  -fuzz 12% -transparent black -trim +repage "$TMP/logo.png"
convert "$ICONO" -fuzz 12% -transparent black -trim +repage "$TMP/badge.png"

for par in "mdpi 108" "hdpi 162" "xhdpi 216" "xxhdpi 324" "xxxhdpi 432"; do
  set -- $par; d=$1; s=$2; dentro=$(( s * 62 / 100 ))
  mkdir -p "$RES/mipmap-$d"
  convert -size "${s}x${s}" xc:none \
    \( "$TMP/logo.png" -resize "${dentro}x${dentro}" \) -gravity center -composite \
    "$RES/mipmap-$d/ic_launcher_foreground.png"
done

for par in "mdpi 48" "hdpi 72" "xhdpi 96" "xxhdpi 144" "xxxhdpi 192"; do
  set -- $par; d=$1; s=$2
  convert "$TMP/badge.png" -resize "${s}x${s}" -background none -gravity center \
    -extent "${s}x${s}" "$RES/mipmap-$d/ic_launcher.png"

  r=$(( s / 2 )); dentro=$(( s * 82 / 100 ))
  convert -size "${s}x${s}" radial-gradient:'#14A83A'-'#04250B' \
    \( -size "${s}x${s}" xc:none -fill white -draw "circle $r,$r $r,0" -alpha copy \) \
    -compose copyopacity -composite \
    \( "$TMP/logo.png" -resize "${dentro}x${dentro}" \) -gravity center -compose over -composite \
    "$RES/mipmap-$d/ic_launcher_round.png"
done

cat > "$RES/values/ic_launcher_background.xml" <<XML
<?xml version="1.0" encoding="utf-8"?>
<!-- Generado por generar-marca.sh. Verde oscuro tomado del propio badge del logo. -->
<resources>
    <color name="ic_launcher_background">#083D14</color>
</resources>
XML

for f in "$RES"/drawable*/splash.png; do
  [ -e "$f" ] || continue
  read -r w h <<< "$(identify -format "%w %h" "$f")"
  menor=$(( w < h ? w : h )); dentro=$(( menor * 62 / 100 ))
  convert -size "${w}x${h}" xc:black \
    \( "$TMP/logo.png" -resize "${dentro}x${dentro}" \) -gravity center -composite "$f"
done

mkdir -p public
convert "$TMP/badge.png" -resize 32x32   -background none -gravity center -extent 32x32   public/favicon-32.png
convert "$TMP/badge.png" -resize 16x16   -background none -gravity center -extent 16x16   public/favicon-16.png
convert "$TMP/badge.png" -resize 180x180 -background none -gravity center -extent 180x180 public/apple-touch-icon.png
convert "$TMP/logo.png" -resize 640x640 public/logo-fexpo.png

echo "Listo. Generado:"
find "$RES" -name "ic_launcher*.png" -newermt '-2 minutes' | wc -l | xargs echo "  iconos del lanzador:"
find "$RES" -name "splash.png" -newermt '-2 minutes' | wc -l | xargs echo "  splash nativos:"
ls -la public/favicon-32.png public/favicon-16.png public/apple-touch-icon.png public/logo-fexpo.png

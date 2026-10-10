# =============================================================================
# Imagen de la base de datos de UniFex: PostgreSQL 16 que se construye sola.
#
# La primera vez que arranca con un volumen VACIO, la imagen oficial de postgres ejecuta en
# orden alfabetico lo que haya en /docker-entrypoint-initdb.d:
#   10-esquema.sh       aplica V0..V49 con aplicar-migraciones.sh (queda registro en
#                       public._migraciones_aplicadas, igual que en un servidor normal)
#   20-datos-prueba.sh  carga docker/db/datos-prueba.sql si CARGAR_DATOS_PRUEBA=true
# Con el volumen ya creado no vuelve a correr nada: los datos persisten entre reinicios.
#
# Se construye con el contexto en la RAIZ del repo (lo hace docker-compose.yml), porque las
# migraciones viven en src/main/resources/db/reserva: una sola fuente de verdad, sin copias.
# =============================================================================
FROM postgres:16

ENV TZ=America/La_Paz \
    PGTZ=America/La_Paz \
    LANG=C.UTF-8

COPY src/main/resources/db/reserva/ /unifex/migraciones/
COPY docker/db/datos-prueba.sql      /unifex/datos-prueba.sql
COPY docker/db/init/                 /docker-entrypoint-initdb.d/

# Si el repo se clono en Windows, los .sh pueden venir con CRLF y bash los rechaza
# ("$'\r': command not found"). Se normalizan aqui para que no dependa de git.
RUN sed -i 's/\r$//' /unifex/migraciones/*.sh /docker-entrypoint-initdb.d/*.sh \
 && chmod +x /unifex/migraciones/*.sh /docker-entrypoint-initdb.d/*.sh

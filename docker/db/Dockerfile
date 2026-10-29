FROM postgres:16

ENV TZ=America/La_Paz \
    PGTZ=America/La_Paz \
    LANG=C.UTF-8

COPY src/main/resources/db/reserva/ /unifex/migraciones/
COPY docker/db/datos-prueba.sql      /unifex/datos-prueba.sql
COPY docker/db/init/                 /docker-entrypoint-initdb.d/

RUN sed -i 's/\r$//' /unifex/migraciones/*.sh /docker-entrypoint-initdb.d/*.sh \
 && chmod +x /unifex/migraciones/*.sh /docker-entrypoint-initdb.d/*.sh

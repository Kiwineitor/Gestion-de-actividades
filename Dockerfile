# =============================================================================
# Imagen de la aplicacion UniFex: backend Spring Boot + SPA Vue en UN solo contenedor.
#
# La SPA se compila con base /app/ y se copia a static/app dentro del jar (lo mismo que hace
# el perfil Maven "frontend"); SpaController la sirve en http://<host>:7676/app/. El sitio
# Thymeleaf viejo sigue en la raiz /. La base de datos NO va aqui: es otro contenedor
# (docker/db/Dockerfile), ver docker-compose.yml y DOCKER.md.
# =============================================================================

# ---- 1. SPA (Vue + Vite) ------------------------------------------------------
FROM node:22-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN VITE_BASE=/app/ npm run build

# ---- 2. Backend (Maven + JDK 21) ----------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
# Primero solo el pom: las dependencias quedan en cache mientras el pom no cambie.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
COPY --from=frontend /frontend/dist ./src/main/resources/static/app
# Sin tests: necesitan una base PostgreSQL viva.
RUN mvn -B -q -DskipTests package && cp target/*.jar /build/app.jar

# ---- 3. Imagen final (solo JRE) -----------------------------------------------
FROM eclipse-temurin:21-jre

ENV TZ=America/La_Paz \
    LANG=C.UTF-8 \
    UNIFEX_UPLOAD_ROOT=/var/lib/unifex/uploads \
    UNIFEX_TMP_DIR=/var/lib/unifex/tmp \
    UNIFEX_LOGS_DIRECTORIO=/var/log/unifex \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=America/La_Paz"

# reportes/ es donde JasperReports busca los .jrxml (relativo al directorio de trabajo).
# No estan en el repo: si se tienen, montarlos ahi (ver DOCKER.md).
RUN groupadd --system unifex \
 && useradd --system --gid unifex --home-dir /opt/unifex unifex \
 && mkdir -p /opt/unifex/reportes /var/lib/unifex/uploads /var/lib/unifex/tmp /var/log/unifex \
 && chown -R unifex:unifex /opt/unifex /var/lib/unifex /var/log/unifex

WORKDIR /opt/unifex
COPY --from=backend --chown=unifex:unifex /build/app.jar app.jar
USER unifex

EXPOSE 7676
HEALTHCHECK --interval=15s --timeout=5s --start-period=120s --retries=5 \
  CMD bash -c ':> /dev/tcp/127.0.0.1/7676' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

FROM node:22-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN VITE_BASE=/app/ npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
COPY --from=frontend /frontend/dist ./src/main/resources/static/app
RUN mvn -B -q -DskipTests package && cp target/*.jar /build/app.jar

FROM eclipse-temurin:21-jre

ENV TZ=America/La_Paz \
    LANG=C.UTF-8 \
    UNIFEX_UPLOAD_ROOT=/var/lib/unifex/uploads \
    UNIFEX_TMP_DIR=/var/lib/unifex/tmp \
    UNIFEX_LOGS_DIRECTORIO=/var/log/unifex \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=America/La_Paz"

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

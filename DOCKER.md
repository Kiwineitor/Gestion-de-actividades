# DOCKER.md — Levantar UniFex con Docker

Esta guía es para quien recibe el proyecto y quiere ejecutarlo sin instalar Java, Node ni
PostgreSQL. Solo hace falta Docker. Complementa a `DEPLOY.md`, que explica la instalación
clásica en un servidor Ubuntu con systemd.

---

## 1. Qué se levanta

```
                 ┌──────────────────────── docker compose ────────────────────────┐
 navegador ───►  │  app  (unifex-app)                    db  (unifex-db)          │
 :7676           │  Spring Boot 3.5 + Java 21   ──JDBC──►  PostgreSQL 16          │
                 │  sirve la API, el WebSocket,           esquema V0..V49 +       │
                 │  la SPA Vue (/app/) y el sitio         datos de prueba         │
                 │  Thymeleaf viejo (/)                                           │
                 │  volúmenes: uploads, logs              volumen: db-data        │
                 └────────────────────────────────────────────────────────────────┘
```

- **`app`**: una sola imagen con el backend y la SPA ya compilada dentro del `.jar`
  (`Dockerfile` en la raíz, de 3 etapas: compila la SPA con Node, compila el jar con Maven y
  deja solo un JRE 21 en la imagen final).
- **`db`**: PostgreSQL 16 con las migraciones y los datos de prueba dentro
  (`docker/db/Dockerfile`). La base se construye sola la primera vez.
- **Volúmenes** (los datos sobreviven a reinicios y a `docker compose down`):
  - `db-data`: la base de datos.
  - `uploads`: fotos, comprobantes y planos que se suben desde la aplicación.
  - `logs`: el registro de errores (`errores.txt`), visible también en el menú *Registro de errores*.

## 2. Requisitos

- Docker Desktop (Windows/Mac) o Docker Engine + plugin Compose v2 (Linux).
  Comprobar: `docker compose version`.
- ~4 GB de RAM libres y ~3 GB de disco.
- Internet la **primera** vez (descarga imágenes base y dependencias; tarda de 5 a 10 minutos).
  Después, arranca en segundos.

## 3. Arranque rápido

Desde la carpeta del proyecto (donde está `docker-compose.yml`):

```bash
docker compose up -d --build
docker compose logs -f app        # esperar "Started UniFexApplication" y salir con Ctrl+C
```

Abrir **http://localhost:7676/app/** e iniciar sesión con alguno de los usuarios de la sección 4.

Sin archivo `.env`, se usan valores de prueba pensados para una máquina local. Para cualquier
otro uso, primero:

```bash
cp .env.example .env              # y editar contraseñas y secretos
```

Comandos del día a día:

| Comando | Qué hace |
|---|---|
| `docker compose ps` | Estado de los contenedores (`healthy` = listo) |
| `docker compose logs -f app` | Ver el log de Java en vivo |
| `docker compose restart app` | Reiniciar solo la aplicación |
| `docker compose down` | Apagar todo. **Los datos se conservan** |
| `docker compose down -v` | Apagar y **borrar** base y archivos subidos (vuelve a nacer con datos de prueba) |
| `docker compose up -d --build` | Recompilar tras cambiar el código |

## 4. Usuarios de prueba

| Usuario | Contraseña | Rol | Para qué sirve |
|---|---|---|---|
| `admin1` | `Admin1Fexpo2026` | SUPER USUARIO | Todo, incluido Mantenimiento y Permisos. Ve todas las pantallas siempre |
| `admin2` | `Admin2Fexpo2026` | ADMINISTRADOR | Administra la feria: plano, precios, usuarios, reportes, credenciales |
| `vendedor1` | `Fexpo2026` | ADMINISTRATIVO | Vende casetas de COMIDA y ARTESANIAS |
| `vendedor2` | `Fexpo2026` | ADMINISTRATIVO | Vende casetas de COMIDA, MYPES y EMPRESAS |
| `control1` | `Fexpo2026` | CONTROL | Puerta: escanea credenciales y registra entradas/salidas |
| `asesoria1` | `Fexpo2026` | ASESORIA | Consulta: tablero, inscripciones, control de ventas, seguimiento de ACYT |

**`admin1` y `admin2` son los usuarios fundadores.** No están en los datos de prueba: los crea
la propia aplicación en su primer arranque (`UniFexApplication`), con las contraseñas de
`ADMIN1_PASSWORD` y `ADMIN2_PASSWORD`. Existen siempre, también en una instalación limpia sin
datos de prueba, y son la puerta de entrada para crear al resto. Esas variables solo se leen la
primera vez: si el usuario ya existe, cambiarlas no hace nada (se cambia la contraseña desde la
aplicación).

Los otros cuatro vienen de `docker/db/datos-prueba.sql`, junto con:

- 8 categorías (COMIDA, EMPRESAS, MYPES, ARTESANIAS, AGROPECUARIA, VIVERO, VEHICULAR,
  PROFESIOGRAFICA), cada una con color, forma, precio y dos opciones de precio
  (*Estándar* y *Con energía eléctrica* +20 %).
- 196 casetas sobre un **plano de ejemplo ficticio** (`frontend/public/mapa.png`), en 8 zonas;
  2 bloqueadas (120 y 121) para ver ese estado. El plano real de una feria se sube desde el
  *Editor del plano* y las casetas se colocan encima.
- Carreras de prueba dentro de las áreas ACEF, ACBN y ACYT.
- 3 ventas de ejemplo de la edición FEXPO 2026, todas **por rendir** (sin comprobante):
  POLLOS A LA BRASA DON BETO (caseta 1, vendedor1), DISTRIBUIDORA AMAZONIA SRL (casetas 33 y 34,
  vendedor2) y ARTESANIAS TACANA (caseta 114, vendedor1).

## 5. Flujo de prueba sugerido

Conviene tener dos navegadores (o una ventana normal y otra privada) para ver el tiempo real.

1. **Administración arma la feria — `admin2`.** *Categorías* (precios y opciones), *Editor del
   plano* (mover o crear casetas), *Vendedores* (qué casetas puede vender cada uno) y
   *Usuarios* (crear más vendedores).
2. **El vendedor vende — `vendedor1`.** *Mapa de ventas*: tocar una caseta libre de COMIDA la
   reserva por 5 minutos y se pinta en el otro navegador al instante. *Registrar venta*:
   datos de la entidad, responsables y forma de pago. La caseta pasa a ocupada y se genera la
   nota de venta en PDF.
3. **Doble venta imposible.** Con `vendedor2` en el otro navegador, intentar la misma caseta de
   COMIDA (la comparten): el sistema la rechaza mientras esté reservada u ocupada.
4. **Rendir el cobro — `vendedor1`.** *Mis ventas* → abrir la venta → adjuntar la foto del
   comprobante. En *Control de ventas* (como `admin2` o `asesoria1`) pasa de *por rendir* a
   *rendido*.
5. **Credenciales — `vendedor1` o `admin2`.** *Credenciales*: generar la de cada responsable,
   con su QR.
6. **Puerta — `control1`.** *Escanear credencial* / *Control de acceso*: registrar entrada y
   salida. Se ve en el reporte de acceso.
7. **Dirección — `asesoria1`.** *Tablero*, *Control de ventas*, *Seguimiento por facultad*.
8. **Permisos — `admin1`.** *Permisos*: dar o quitar pantallas por rol o a un usuario concreto.

## 6. La base de datos

### Cómo se crea

La imagen oficial de PostgreSQL ejecuta los scripts de `/docker-entrypoint-initdb.d` **una sola
vez: cuando el volumen `db-data` está vacío**.

1. `docker/db/init/10-esquema.sh` aplica, en orden, todos los
   `src/main/resources/db/reserva/V*.sql` con `aplicar-migraciones.sh`. Es el mismo script que
   se usa en un servidor normal, y deja el registro en `public._migraciones_aplicadas`.
2. `docker/db/init/20-datos-prueba.sh` carga `docker/db/datos-prueba.sql` solo si
   `CARGAR_DATOS_PRUEBA=true`, que es el valor por defecto.

Después arranca Java, ve que el esquema ya existe y solo crea los roles que falten y a
`admin1`/`admin2`. Hibernate **nunca** modifica el esquema (`hbm2ddl.auto=none`).

- **Instalación limpia sin datos de prueba:** `CARGAR_DATOS_PRUEBA=false` en `.env` y
  `docker compose down -v && docker compose up -d`.
- **Volver a empezar desde cero:** `docker compose down -v` borra el volumen y la próxima
  subida reconstruye todo.
- Cambiar `datos-prueba.sql` o agregar una migración `V50__...sql` **no afecta a una base ya
  creada**. Hay que recrearla (`down -v`) o aplicar el script a mano (ver abajo).

### Entrar a la base

Desde la propia máquina, con pgAdmin o DBeaver: host `localhost`, puerto `5433`, base `unifex`,
usuario `unifex`, contraseña `unifex_dev_2026` (o lo que diga `.env`). Por consola:

```bash
docker compose exec db psql -U unifex -d unifex
```

### Respaldar y restaurar

```bash
# respaldo (formato custom, comprimido)
docker compose exec -T db pg_dump -U unifex -d unifex -Fc > respaldo_unifex.dump

# restaurar sobre la base del contenedor (BORRA lo que haya)
docker compose exec -T db pg_restore -U unifex -d unifex --clean --if-exists < respaldo_unifex.dump
```

### Aplicar una migración nueva a una base existente

```bash
docker compose cp src/main/resources/db/reserva/V50__algo.sql db:/tmp/
docker compose exec db psql -U unifex -d unifex -v ON_ERROR_STOP=1 -f /tmp/V50__algo.sql
```

### Usar una base de datos externa (por ejemplo, la de producción)

El contenedor `db` es para probar. Para conectar la aplicación a un PostgreSQL que ya existe:
levantar solo `app` (`docker compose up -d --no-deps app`) y en `.env`/`docker-compose.yml`
apuntar `SPRING_DATASOURCE_URL` a ese servidor
(`jdbc:postgresql://servidor:5432/base`), con su usuario y `DB_PASSWORD`. Esa base debe tener
ya todas las migraciones aplicadas; ver `DEPLOY.md` §2. **Nunca** cargar `datos-prueba.sql` en
ella.

## 7. Configuración (`.env`)

| Variable | Por defecto (solo pruebas) | Qué es |
|---|---|---|
| `DB_NAME` / `DB_USER` / `DB_PASSWORD` | `unifex` / `unifex` / `unifex_dev_2026` | Credenciales de la base del contenedor |
| `DB_PUERTO_LOCAL` | `5433` | Puerto en esta máquina para entrar a la base (solo 127.0.0.1) |
| `CARGAR_DATOS_PRUEBA` | `true` | Sembrar datos de prueba al crear la base |
| `APP_PUERTO` | `7676` | Puerto web |
| `API_KEY` | valor de prueba | Clave del API interno (`X-API-KEY`) |
| `JWT_SECRET` | valor de prueba | Firma de las sesiones de la SPA/APK. 32 caracteres o más |
| `ADMIN1_PASSWORD` / `ADMIN2_PASSWORD` | `Admin1Fexpo2026` / `Admin2Fexpo2026` | Contraseñas iniciales de los fundadores |
| `UNIFEX_PUBLICO_BASE_URL` | `http://localhost:7676` | URL pública (enlaces y QR) |
| `NOTA_VERIFICACION_URL` | vacío | URL que va en el QR de la nota de venta |

Si el sistema va a quedar expuesto en una red, hay que cambiar **todos** los valores de prueba:
son públicos, porque están escritos en este archivo.

## 8. Entregar sin el código fuente

Si quien lo recibe no va a compilar, se le pueden pasar las imágenes ya construidas:

```bash
# en la máquina que compiló
docker compose build
docker save unifex-app:latest unifex-db:latest -o unifex-imagenes.tar
# entregar: unifex-imagenes.tar + docker-compose.yml + .env.example

# en la máquina destino
docker load -i unifex-imagenes.tar
docker compose up -d --no-build
```

(Otra opción es publicarlas en un registro como Docker Hub o GitHub Container Registry).

## 9. Limitaciones conocidas

- **Migraciones V32–V47.** No llegaron al repositorio. Las reemplaza
  `V47__reconstruccion_v32_a_v47.sql`, que se dedujo del código y se verificó arrancando la
  aplicación, pero los tipos pueden no coincidir exactamente con la base real. Es idempotente:
  si aparecen los originales, se agregan sin conflicto.
- **Reportes Jasper** (algunos XLSX/DOCX): los `.jrxml` no están en el repo. Si se tienen,
  montarlos en `/opt/unifex/reportes` (línea comentada en `docker-compose.yml`). El resto del
  sistema funciona sin ellos.
- **"Quiénes están dentro" (detalle)** en Control de acceso responde error 500: es un fallo del
  SQL de `CredencialAccesoDao.quienesEstanDentro` (falta un `GROUP BY`), no de Docker. Ocurre
  igual fuera de Docker; el resto del control de acceso funciona.
- **WhatsApp** queda desactivado. Se configura desde la pantalla *Instancias de WhatsApp*.
- **Pasarela de pago / boletería**: apagada, como en `DEPLOY.md` §4.
- Sin HTTPS: para exponerlo fuera de la red local, poner delante un proxy (Nginx, Caddy,
  Traefik) con certificado. La aplicación ya entiende las cabeceras `X-Forwarded-*`.

## 10. Problemas frecuentes

| Síntoma | Causa y solución |
|---|---|
| `app` reinicia una y otra vez | `docker compose logs app`. Si dice que falta una variable, revisar `.env` |
| La base no se cargó con los datos nuevos | El volumen ya existía: `docker compose down -v` y volver a subir |
| `port is already allocated` | Otro programa usa 7676 o 5433: cambiar `APP_PUERTO` / `DB_PUERTO_LOCAL` |
| `$'\r': command not found` en `db` | Scripts con fin de línea Windows. La imagen ya los corrige; reconstruir con `--build` |
| Login responde 401 con la contraseña correcta de admin1 | La base se creó con otra `ADMIN1_PASSWORD`: vale la del primer arranque |

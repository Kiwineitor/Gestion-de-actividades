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
                 │  sirve la API, el WebSocket,           esquema V0..V80 +       │
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

Abrir **http://localhost:7676/app/admin** (panel de administración) e iniciar sesión con alguno de
los usuarios de la sección 4. La página pública, la que ven los clientes, es **http://localhost:7676/app/**.

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
| `asesoria1` | `Fexpo2026` | ASESORIA | Consulta: inscripciones, rendición de cuentas y reportes |

**Primer ingreso obligatorio.** Al entrar por primera vez con `admin1` o `admin2` (o con cualquier usuario al que un administrador le haya puesto o restablecido la contraseña) el sistema pide aceptar los términos de uso y cambiar la contraseña inicial por una propia de al menos 10 caracteres con letras y números. Hasta hacerlo, la API responde 403 a todo lo demás. Cinco contraseñas equivocadas seguidas bloquean el usuario 15 minutos.

**Sello de integridad.** Cada cuenta lleva una firma calculada con `JWT_SECRET` (o `SELLO_SECRETO` si se define). Si alguien cambia la contraseña o el rol directamente en la base, la firma deja de coincidir y la cuenta queda bloqueada. Para recuperar un acceso se arranca una vez con `UNIFEX_RESTABLECER_USUARIO` y `UNIFEX_RESTABLECER_CLAVE` en `.env` (la clave temporal obliga a cambiarla al entrar) y luego se quitan esas dos líneas. Si se cambia `JWT_SECRET`/`SELLO_SECRETO`, hay que arrancar una vez con `UNIFEX_RESELLAR=true`.

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
- Dos actividades de ejemplo: la feria **«Feria de ejemplo 2026»** (en curso, con el plano y las
  casetas de arriba) y el evento con mesas **«Noche Neón»** (22 mesas en zonas VIP, General y
  Barra). Ninguna tiene ventas ni reservas: la base arranca limpia para probar desde cero.
- Plantillas de credencial: la «Credencial virtual» (ferias) y el «Pase de evento» (eventos con
  mesas: foto o logo del local en rombo y QR de entrada y salida).

## 5. Flujo de prueba sugerido

Conviene tener dos navegadores (o una ventana normal y otra privada) para ver el tiempo real.
El panel está en `/app/admin` y la página pública en `/app/`.

**Feria con puestos**

1. **Arma la feria — `admin2`.** *Centro de actividades* → «Feria de ejemplo 2026» → *Editor del
   plano*: mover o crear casetas y, con el botón *Categorías y precios*, fijar precios y personas
   por puesto. Una feria nueva la pone «en curso» solo `admin1`.
2. **Diseño de credencial.** *Credenciales* → *Diseño*: duplicar una plantilla y elegir arriba
   «¿Para qué actividad es este diseño?».
3. **El cliente pide un espacio.** En `/app/`, entrar a la feria, elegir un espacio libre y enviar
   la solicitud con su comprobante.
4. **Aprobar = vender.** *Solicitudes* → *Ferias* → *Aprobar*: se registra la venta y sale la
   entrega con recibo, credenciales por persona y botón de WhatsApp.
5. **Venta en oficina — `vendedor1`.** *Mapa de ventas*: tocar una caseta libre, *Agregar* y
   *Registrar venta*. Con `vendedor2` en el otro navegador, la misma caseta aparece tomada al
   instante: la doble venta es imposible.

**Evento con mesas (Noche Neón)**

6. **Prepara el evento — `admin2`.** *Centro de actividades* → «Noche Neón» → *Organizar evento*:
   datos, QR de pago, logo del local («Identidad del local») y plano de mesas.
7. **El cliente reserva.** En `/app/`, elegir Noche Neón, una mesa y pagar con el QR; puede salir
   a la app del banco y volver para adjuntar el comprobante.
8. **Aprobar.** *Solicitudes* → *Mesas* → *Aprobar* con la cantidad de pases. En *Recibo y pases*,
   «✏️ Datos» carga nombre y foto de cada persona; sin datos, el pase lleva el logo del local.
9. **Venta en oficina.** *Mapa de ventas* → elegir «Noche Neón» → tocar una mesa libre → *Vender*.

**Para las dos**

10. **Puerta — `control1`.** *Ingreso y accesos* → *Escanear*: cada lectura alterna entrada y
    salida. *Movimientos* muestra quién entró y quién sigue adentro.
11. **Seguimiento.** *Inscripciones* → *Todas* (ventas y reservas juntas, con «Recibo y
    credenciales» para reenviar), *Reportes* (con selector de actividad, Excel y PDF) y *Mis ventas*.
12. **Permisos — `admin1`.** *Roles y permisos*: dar o quitar pantallas por rol o a un usuario.
13. **Eliminar una actividad.** *Centro de actividades* → menú ⋮ → *Eliminar* (pide escribir el
    nombre; no deja si ya tiene ventas o reservas).

## 6. La base de datos

### Entorno aislado para las mejoras de actividades

Las mejoras se prueban sobre `unifex_mejoras`, una copia de la base local `unifex` dentro del mismo PostgreSQL de Docker. La aplicación de revisión usa el puerto **7677** y volúmenes propios para archivos y logs. La base original `unifex` no recibe las migraciones ni las escrituras de esa aplicación.

En una instalación local nueva, iniciar primero solo PostgreSQL y crear la copia mientras no haya conexiones a `unifex`:

```powershell
docker compose up -d db
docker compose exec -T db psql -U unifex -d postgres -v ON_ERROR_STOP=1 -c 'CREATE DATABASE unifex_mejoras WITH TEMPLATE unifex OWNER unifex'
Get-Content -Raw 'src/main/resources/db/reserva/local/retirar_modulos_descartados.sql' | docker compose exec -T db psql -U unifex -d unifex_mejoras -v ON_ERROR_STOP=1
docker compose -f docker-compose.yml -f docker-compose.mejoras.yml up -d --build app-mejoras
```

Abrir `http://localhost:7677/app/`. Si la base original ya está en uso, cerrar primero las aplicaciones conectadas o hacer la copia desde un respaldo: PostgreSQL no permite `CREATE DATABASE ... TEMPLATE` mientras haya sesiones en la base de origen. Si `unifex_mejoras` ya existe, **no** volver a ejecutar la creación; el contenedor conserva los cambios de prueba.

El SQL de retirada está en `db/reserva/local/` para que el ejecutor habitual de migraciones no lo aplique a otras bases. Se ejecuta de forma explícita únicamente contra `unifex_mejoras`.

Los archivos subidos se guardan en `uploads-mejoras`. Si la base original contiene archivos que la copia necesita mostrar, copiar también el contenido de `unifex_uploads` a `unifex_uploads-mejoras` antes de iniciar `app-mejoras`; la base y los archivos son dos respaldos distintos. En el entorno local revisado el 2026-10-22, el volumen de origen no contenía archivos.

**No ejecutar `docker compose down -v` durante este trabajo:** elimina el volumen compartido de PostgreSQL y con él tanto `unifex` como `unifex_mejoras`. `docker compose down` sin `-v` conserva las bases.

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
| Login responde 401 con la contraseña correcta de admin1 | La base se creó con otra `ADMIN1_PASSWORD`: vale la del primer arranque, o la que pusiste en el primer ingreso. Para recuperarla: `UNIFEX_RESTABLECER_USUARIO` / `UNIFEX_RESTABLECER_CLAVE` |
| «Demasiados intentos fallidos» | Cinco contraseñas equivocadas: esperar 15 minutos |
| «Esta cuenta fue modificada fuera del sistema» | Se tocó la contraseña o el rol en la base. Restablecer con `UNIFEX_RESTABLECER_USUARIO` / `UNIFEX_RESTABLECER_CLAVE` |

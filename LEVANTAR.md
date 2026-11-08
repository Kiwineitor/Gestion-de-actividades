# Cómo levantar el sistema con Docker

Hola, te dejo los pasos directos para que lo tengas corriendo en tu máquina. Solo necesitas Docker Desktop abierto y Git. No hace falta instalar Java, Node ni PostgreSQL.

## 1. Clonar

```bash
git clone https://github.com/Kiwineitor/Gestion-de-actividades.git
cd Gestion-de-actividades
```

## 2. Levantar

```bash
docker compose up -d --build
```

La primera vez tarda entre 5 y 10 minutos, porque descarga las imágenes y compila todo. Para ver cuándo está listo:

```bash
docker compose logs -f app
```

Cuando aparezca `Started UniFexApplication`, sal con `Ctrl + C`. La aplicación sigue corriendo.

## 3. Entrar

| Qué | Dirección |
|---|---|
| Panel de administración | http://localhost:7676/app/admin |
| Página pública (la que ven los clientes) | http://localhost:7676/app/ |

Usuario inicial:

| Usuario | Contraseña inicial | Rol |
|---|---|---|
| `admin1` | `Admin1Fexpo2026` | Super usuario (todo) |
| `admin2` | `Admin2Fexpo2026` | Administrador |

**La primera vez que entres** el sistema te pide dos cosas antes de dejarte pasar:

1. Leer y aceptar los términos de uso.
2. Cambiar la contraseña inicial por una tuya: mínimo 10 caracteres, con letras y números, y sin tu nombre de usuario.

Esa contraseña inicial solo sirve para ese primer ingreso. Después ya no vale.

Al guardar tu contraseña el sistema te muestra un **código de recuperación** (algo como `K7QM-3XRP-…`). Guárdalo bien: si un día olvidas la contraseña, con ese código creas una nueva tú mismo desde «¿Olvidaste tu contraseña?».

El manual de usuario, con capturas de cada pantalla, está en `docs/Manual-de-usuario.pdf`.

También vienen usuarios de prueba (`vendedor1`, `vendedor2`, `control1`, `asesoria1`, todos con `Fexpo2026`) y una feria de ejemplo para practicar.

## 4. Comandos que vas a usar

| Comando | Para qué |
|---|---|
| `docker compose ps` | Ver si está corriendo (`healthy` = listo) |
| `docker compose logs -f app` | Ver el log en vivo |
| `docker compose down` | Apagar. Los datos se conservan |
| `docker compose up -d` | Volver a encender |
| `git pull && docker compose up -d --build` | Actualizar cuando yo suba cambios |
| `docker compose down -v` | Apagar y **borrar todo** (base y archivos). Vuelve a nacer con datos de prueba |

## 5. Si algo falla

- **`port is already allocated`**: tienes algo usando el 7676 o el 5433. Crea un archivo `.env` con `APP_PUERTO=7680` y `DB_PUERTO_LOCAL=5440` (o los puertos que tengas libres) y vuelve a levantar.
- **Te pide esperar antes de volver a intentar**: te equivocaste varias veces de contraseña. La pausa es corta (1 minuto, luego 5 y como máximo 15) y solo en ese equipo. Antes de la pausa el sistema avisa cuántos intentos te quedan.
- **Olvidaste tu contraseña**, en este orden:
  1. En la pantalla de ingreso, «¿Olvidaste tu contraseña?», con tu usuario y tu código de recuperación.
  2. Otro administrador entra a *Usuarios* y te pone una contraseña nueva (y, si estás en pausa, «Quitar pausa de ingreso»).
  3. Si nadie puede entrar, agrega esto al `.env`, ejecuta `docker compose up -d` y entra con esa clave temporal (te pedirá cambiarla). Luego borra esas dos líneas y vuelve a ejecutar `docker compose up -d`.
  ```
  UNIFEX_RESTABLECER_USUARIO=admin1
  UNIFEX_RESTABLECER_CLAVE=Temporal-2026-xyz
  ```
- **Cambiaste algo y no se ve**: `docker compose up -d --build`.

Esto es para trabajar en local. Si algún día se instala en un servidor de verdad, primero hay que copiar `.env.example` a `.env` y cambiar **todas** las claves (base de datos, `JWT_SECRET`, `API_KEY`, contraseñas iniciales) y poner `CARGAR_DATOS_PRUEBA=false`. El detalle está en `DOCKER.md` y `DEPLOY.md`.

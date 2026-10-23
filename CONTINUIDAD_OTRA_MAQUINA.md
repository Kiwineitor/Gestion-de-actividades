# Continuidad de UniFex en otra máquina

**Fecha:** 23 de octubre de 2026 (Bolivia). Este documento resume nuestras conversaciones y el estado comprobado del proyecto; no es una transcripción literal del chat.

## Qué pidió el usuario y qué quedó decidido

El documento original `Mejoras sistema de ferias.pdf` propone mejorar navegación, credenciales, escáner, categorías, plano, ventas, usuarios, roles y permisos, y retirar módulos que ya no se necesitan. El usuario decidió que el producto debe servir para **actividades de distintos tipos**, incluidas ferias y eventos. La reserva directa de mesas para boliches queda para una etapa posterior.

Decisiones expresas del usuario:

1. Noticias, Noches de FEXPO y Seguimiento por facultad dejan de ser necesarios. Antes de borrar datos, crear una copia local de la base y trabajar sobre ella. No se autorizó ejecutar el borrado en producción.
2. Medidas como `3 × 3 m` son **etiquetas comerciales variables**. El dibujo del puesto debe poder ajustarse a cualquier medida; la medida no impone por sí sola su escala en el plano. Solo `ADMINISTRADOR` y `SUPER USUARIO` diseñan el plano.
3. Credenciales: editar tanto la plantilla como los datos necesarios de la persona, con vista previa y trazabilidad.
4. Escáner: abrir en Entrada; cualquier operador autorizado puede activar Salida cuando la necesite.
5. Ventas debe reunir registro, consulta, comprobantes, pendientes y exportaciones. Dashboard/Reportes queda para análisis.
6. El usuario autorizó consultas y accesos necesarios para trabajar y pidió evitar interrupciones repetidas con solicitudes de aprobación para comandos rutinarios.

El plan detallado y los pendientes están en `PLAN_MEJORAS_ACTIVIDADES.md`. Las instrucciones técnicas obligatorias del proyecto están en `AGENTS.md`; leer además `CLAUDE.md`, `PLAN.md` y las skills `.claude/skills/unifex-fullstack/` y `unifex-arquitectura/` antes de cambios de dominio o borrados. `README.md` contiene datos técnicos obsoletos.

## Estado del código al preparar el traslado

La rama es `main`, último commit existente `15ff03f`. **Hay cambios sin commit**: el paquete de código fuente debe conservar el árbol de trabajo completo. El archivo Git bundle sirve para recuperar el historial previo; no contiene esos cambios recientes.

Hecho en el árbol de trabajo:

- Entorno Docker de revisión `docker-compose.mejoras.yml`: aplicación en `http://localhost:7677/app/`, conectada a `unifex_mejoras`; base original `unifex` en el mismo PostgreSQL y volúmenes de archivos y logs separados.
- Menú reorganizado para actividades; Comunicados y Sistema agrupados; Seguimiento en vivo fuera del menú sin desactivar la lógica interna de presencia y reservas.
- Escáner abre en Entrada y ofrece Salida a operadores con acceso a su pantalla; la API acepta permiso de pantalla además del rol de control.
- Retirados del código y la página pública Noticias, Noches de FEXPO y Seguimiento por facultad: rutas Vue, vistas, controladores, servicios, entidades, repositorios y suscripciones WebSocket correspondientes. Los scripts históricos V26/V47 se conservan como historia del esquema.
- `src/main/resources/db/reserva/local/retirar_modulos_descartados.sql` borra tablas `noticia`, `noche_fexpo` y permisos de esas pantallas. **Está fuera del patrón `V*.sql` para impedir que el proceso normal de migración lo aplique a producción.** Se ejecutó solo en `unifex_mejoras`.
- Usuarios tiene filtros combinables de texto, rol y estado, contador de resultados y acciones de fila con nombres legibles.
- Se conservaron las pruebas del contador de visitas en `VisitasPaginaTest` al retirar las pruebas de música de Noches.

Últimas verificaciones completadas: compilación de backend y frontend en Docker; 33 pruebas Node pasaron; pruebas de humo `verificar-api.mjs` y `verificar-venta.mjs` pasaron contra el puerto 7677; `/app/`, `/app/feria` y `/api/publico/feria` respondieron 200; las rutas públicas `/noches` y `/noticias` respondieron 404. En la copia ya no están las tablas retiradas, mientras que en `unifex` sí permanecen. La prueba visual en navegador queda pendiente.

## Qué falta hacer

Seguir `PLAN_MEJORAS_ACTIVIDADES.md`, sin dar por completadas las fases restantes:

1. Revisar visualmente menú, escáner, Usuarios y página pública en la nueva máquina. Confirmar también que las rutas de los módulos retirados no se ofrecen en permisos.
2. Generalizar la configuración por actividad y decidir qué datos pertenecen a cada edición. Crear una actividad piloto distinta de FEXPO.
3. Mejorar Categorías y Editor: medida comercial independiente de geometría, modos Gestión/Diseño, permisos del servidor, navegación del plano, herramientas y sincronización. Respetar los `UPDATE` condicionales y WebSocket que impiden vender dos veces un puesto.
4. Implementar editor completo de credenciales: plantilla, datos, vista previa y auditoría.
5. Consolidar Ventas, Reportes/Dashboard, Roles/Permisos, Vendedores y Personal/Dependencias.
6. Decidir con el usuario el destino final de registros y archivos históricos **en producción** antes de cualquier borrado allí.

No usar el perfil Spring por defecto fuera de Docker sin revisar el destino: puede apuntar a producción. En desarrollo local usar `dev` con una configuración privada derivada de `application-dev.properties.example`. No añadir claves locales al repositorio ni al paquete de código.

## Recuperación en la otra máquina

El paquete de traslado debe contener el árbol de código, un Git bundle y los volcados PostgreSQL `unifex_mejoras.dump` y `unifex_origen.dump` si Docker estaba disponible al crearlo. Para continuar, instalar Java 21, Node y Docker Desktop; extraer el código y revisar `DOCKER.md`.

Si se incluye `unifex_mejoras.dump`, iniciar PostgreSQL, crear una base **vacía** llamada `unifex_mejoras` con dueño `unifex`, y restaurar el dump con `pg_restore --no-owner --no-acl`. Después iniciar `app-mejoras` con el archivo Compose adicional. El dump de `unifex_origen` es un respaldo separado y no hace falta restaurarlo para trabajar en las mejoras. Si falta el dump, crear la copia desde la base local conforme a `DOCKER.md` y aplicar el SQL local de retirada solo a la copia.

Los dumps pueden incluir datos personales y contraseñas cifradas de prueba; guardar el paquete en un lugar privado. El PDF original se incluye en el paquete si estaba disponible durante la copia.

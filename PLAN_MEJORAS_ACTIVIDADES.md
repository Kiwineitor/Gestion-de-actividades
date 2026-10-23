# Plan de mejoras para actividades

**Estado:** en ejecución, 2026-10-23. Las decisiones abiertas se resuelven antes de modificar datos de producción.

## Objetivo

Evolucionar UniFex para administrar actividades con puestos asignables, como ferias y eventos, sin depender de nombres, medidas o pantallas propias de FEXPO. Se conserva el circuito actual de venta, acreditación y control de acceso. La reserva directa de mesas para boliches se analiza como producto posterior; no forma parte de esta implementación.

Este documento traduce las propuestas de `Mejoras sistema de ferias.pdf` a trabajo verificable. El PDF es una fuente de propuestas, no una especificación aprobada en todos sus detalles. `CLAUDE.md` describe la arquitectura y `PLAN.md` registra el trabajo anterior; ambos contienen referencias que deben contrastarse con el código actual.

## Decisiones confirmadas

1. El producto debe servir para distintas clases de actividades, no solo ferias.
2. Noticias, Noches de FEXPO y Seguimiento por facultad dejan de ser módulos necesarios. La retirada se desarrolla primero sobre una copia local de la base; se conserva intacta la base de origen. El destino definitivo de los datos históricos en producción sigue pendiente de precisar.
3. Medidas como `3 × 3 m` son etiquetas comerciales editables; no determinan por sí solas la geometría del plano. Deben admitirse medidas distintas y una escala visual ajustable. Solo `ADMINISTRADOR` y `SUPER USUARIO` podrán diseñar el plano.
4. La edición de credenciales abarcará plantilla y datos personales necesarios, con vista previa y registro de cambios.
5. Ventas reunirá registro, consulta, comprobantes, cobros pendientes y exportaciones. Dashboard/Reportes concentrará el análisis.
6. Cualquier operador autorizado para escanear podrá habilitar Salida cuando la necesite; Entrada será el flujo inicial.
7. La base de trabajo es `unifex_mejoras`, copia local de `unifex` en PostgreSQL Docker. La aplicación de revisión se prepara en `docker-compose.mejoras.yml`, en el puerto 7677, con volúmenes de archivos y logs propios.

## Estado encontrado en el código

| Área | Base reutilizable | Trabajo real |
|---|---|---|
| Actividades | `Edicion` identifica la edición activa y admite plano por edición. | Revisar textos, catálogos, reglas y rutas que presuponen FEXPO o feria. Definir los datos configurables de cada actividad antes de ampliar el esquema. |
| Módulos a retirar | Rutas Vue y entradas del menú. Noticias también alimenta la página pública; los anuncios son otra función y se conservan. | Inventariar referencias, retirar accesos y servicios según el alcance acordado, migrar o respaldar datos y archivos según la decisión pendiente. |
| Credenciales | PDF e imagen generados en servidor, plantillas seleccionables e historial de impresión. | Configuración editable y persistente de plantilla, vista previa fiel, edición autorizada de datos y auditoría de modificaciones y reemisiones. |
| Escáner | API de entrada y salida, historial y avisos de movimiento repetido. | Entrada como flujo inicial; habilitación explícita de Salida según la decisión de roles/configuración. Mantener validación del lado servidor. |
| Categorías | Nombre, color, precio, formas básicas y tamaño visual. La caseta tiene escala y rotación. | Nuevas formas, vista previa, controles visuales y separación clara entre medida comercial, tamaño de categoría y escala de cada caseta. |
| Editor | Selección, colocación, texto, rejilla, numeración, guardado y WebSocket. | Rediseñar la interacción en modos Gestión/Diseño; comprobar los fallos reportados; reducir paneles persistentes sin perder herramientas. |
| Ventas y análisis | Venta, Control de ventas, Reportes y Dirección ya tienen consultas y exportaciones. | Reordenar las tareas, eliminar duplicación de interfaz y conservar permisos, cifras y archivos exportados. |
| Administración | Roles, Permisos, Usuarios, Vendedores y Personal de apoyo existen. Personal y Dependencias ya comparten pestañas. | Consolidar navegación, filtros y acciones; proteger roles del sistema y mantener permisos por rol y por usuario. |

## Avance de la primera entrega (2026-10-22)

- [x] Base local `unifex_mejoras` copiada y aplicación de revisión aislada en `localhost:7677/app/`.
- [x] Noticias, Noches de FEXPO y Seguimiento en vivo retirados del menú; Seguimiento por facultad también se retiró del menú. Los procesos internos de presencia y reserva siguen activos.
- [x] Anuncios se muestra como Comunicados; Mantenimiento y Registro de errores se agrupan en Sistema.
- [x] La navegación interna agrupa las pantallas por Actividad y usa un título general; la página pública de FEXPO conserva su nombre propio.
- [x] Escáner: cada visita abre en Entrada; Salida se elige desde una opción secundaria y permanece activa durante esa visita para permitir tandas de salida. La API admite también a quien tenga asignada la pantalla Escáner, aunque su rol sea personalizado.
- [x] Retiradas las rutas, vistas, API, servicios y suscripciones públicas de Noticias, Noches de FEXPO y Seguimiento por facultad. La página pública ya no consulta ni muestra esos contenidos.
- [x] El SQL local `db/reserva/local/retirar_modulos_descartados.sql` elimina las tablas `noticia` y `noche_fexpo` y los permisos de las tres pantallas. Se aplicó únicamente en `unifex_mejoras`; se verificó que `unifex` conserva ambas tablas. Quedó fuera de la secuencia automática de migraciones.
- [x] La aplicación de revisión compila y arranca con la copia sin esas tablas. `GET /api/publico/feria` responde 200 sin esos contenidos y las dos rutas públicas retiradas responden 404.
- [x] Compilación Vue y 33 pruebas Node correctas en Docker; las pruebas de humo de reserva y venta pasaron contra `localhost:7677`.
- [x] Usuarios: filtros combinables de texto, rol y estado; contador de resultados y acciones de cada fila en un menú con nombres legibles.
- [ ] Completar la prueba visual de los cambios de menú y escáner en el entorno Docker actualizado.

## Fases de ejecución

### Fase 0 — Reglas y línea base

- Resolver las decisiones abiertas de este documento.
- Inventariar rutas, tablas, archivos subidos, permisos y consumidores de cada módulo afectado. Comprobar los problemas de Rejilla, Números y cámara del plano en una sesión real.
- Registrar en `PLAN.md` las tareas antiguas que ya existen en el código y las nuevas tareas aprobadas.
- Definir una muestra de actividad distinta de FEXPO para validar la generalización, sin crear todavía un segundo producto.

**Salida:** alcance aprobado, inventario de dependencias y criterios de aceptación por pantalla. Ningún borrado de datos en esta fase.

### Fase 1 — Estructura para varias actividades

- Revisar qué elementos dependen de la edición activa: plano, categorías, precios, textos, credenciales, comunicaciones, ventas y reportes.
- Sustituir en las pantallas operativas los rótulos específicos de FEXPO cuando corresponda. Conservar los nombres históricos en documentos ya emitidos.
- Definir qué se configura por actividad y qué es global. Cualquier columna nueva irá en un SQL numerado e idempotente; no hay migraciones automáticas.
- Probar el cambio de actividad y el aislamiento de datos entre ediciones.

**Salida:** una actividad nueva puede configurarse sin cambiar código para lo que el alcance de esta fase cubra. La creación completa de actividades desde la interfaz se estima después del inventario.

### Fase 2 — Navegación y operación diaria

- Retirar los módulos acordados del menú y de la interfaz, después de verificar consumidores públicos y permisos. La eliminación de tablas, registros o archivos se hará como trabajo separado y revisable.
- Mantener Seguimiento en vivo fuera del menú si se confirma su retiro; conservar presencia, sesiones y bloqueo temporal de casetas.
- Reubicar Anuncios como Comunicados o Avisos generales. Agrupar Mantenimiento y Registro de errores bajo Sistema, restringido a perfiles autorizados.
- Hacer que el escáner abra en Entrada. Mantener la operación de Salida según la regla de acceso acordada.
- Añadir filtros de Usuarios y acciones legibles en menú contextual; aplicar el mismo patrón donde aporte claridad.

**Salida:** navegación corta y operación principal sin pasos innecesarios, con las rutas y permisos comprobados.

### Fase 3 — Categorías y Editor del plano

- Separar modo Gestión (consulta y cambios comerciales permitidos) de modo Diseño (geometría, creación y disposición), con autorización del servidor para Diseño.
- Usar arrastre de fondo para navegar, clic en elemento para seleccionar, rueda para zoom y Esc para cerrar. Evitar que una consulta mueva una caseta.
- Abrir un panel contextual de puesto con datos existentes. Definir explícitamente cada escritura: datos comerciales del puesto, tarifa vigente o venta registrada. El estado de pago se muestra desde la venta, sin añadir un quinto estado a `Puesto`.
- Mostrar numeración según zoom y la preferencia Números. Verificar Rejilla y Números antes de reescribirlos. Hacer contextuales los controles de texto.
- Conservar cámara al cambiar herramientas y guardar; actualizar localmente y confirmar con la difusión del servidor sin recargar la lista entera.
- Añadir formas aprobadas y controles de ancho/alto visual, con previsualización. Guardar la medida comercial como dato independiente de la escala del dibujo.

**Salida:** edición usable en plano grande, con movimientos intencionales y sincronización correcta entre usuarios.

### Fase 4 — Credenciales completas

- Definir plantillas por actividad, imagen de fondo, campos visibles, posiciones, tamaño y textos. Ofrecer vista previa generada por la misma disposición usada al imprimir.
- Permitir corregir nombre, foto, cargo/tipo y otros datos aprobados desde un flujo autorizado. Precisar qué entidad es la fuente de verdad y registrar autor, momento y valor anterior de cambios sensibles.
- Mantener QR verificable, emisión por lotes e historial de impresiones. Probar reemisión y credenciales previamente emitidas.

**Salida:** la configuración persiste, la vista previa coincide con el PDF y los cambios de datos son trazables.

### Fase 5 — Administración y ventas

- Consolidar Roles y Permisos en una pantalla con Información, Permisos y Usuarios; preservar permisos por rol y por usuario. Proteger identificadores de roles del sistema.
- Mejorar Vendedores y Personal de apoyo/Dependencias sobre las funciones existentes.
- Organizar Ventas para operación y Dashboard/Reportes para análisis; comparar resultados con los módulos actuales antes de retirar duplicaciones. Mantener exportaciones y recorte por vendedor.

**Salida:** tareas administrativas y de análisis con un lugar claro, sin pérdida de capacidades ni ampliación accidental de acceso.

### Fase 6 — Evaluación del producto de mesas

- Documento separado para clientes, eventos, mesas, cupos, reserva temporal, confirmación de pago, QR, beneficios, ingreso, no presentación, liberación y modelo comercial.
- Identificar qué componentes de UniFex se reutilizan y cuáles necesitan aislamiento por cliente y evento. No conectar una pasarela ni aceptar pagos hasta definir proveedor, conciliación y cancelaciones.

**Salida:** alcance y estimación de un piloto; su implementación requiere una decisión posterior.

## Criterios transversales

- Toda transición de caseta se arbitra con `UPDATE` condicional en PostgreSQL y se comunica por WebSocket después de confirmar la transacción. La interfaz no decide por sí sola si un puesto quedó vendido.
- `Puesto.estado_puesto` y el estado de cobro son conceptos distintos. Las ventas históricas conservan su precio registrado aunque cambie la tarifa vigente.
- Las autorizaciones se comprueban en la API, además de ocultar controles en Vue. El acceso a ventas ajenas sigue las reglas actuales de `AccesoPantallas`.
- Las retiradas de código se revisan también en plantillas Thymeleaf, SQL, rutas públicas, WebSocket y archivos fuera del repositorio antes de borrar símbolos.
- Cada fase termina con pruebas del flujo afectado, permisos, navegador y, cuando toque reservas, concurrencia. Las pruebas de backend usan el perfil `dev` y una base local; el perfil por defecto apunta a producción.

## Decisiones abiertas

1. Retirada definitiva en producción: ¿se borran registros y archivos históricos tras un respaldo verificado, o se conserva un archivo consultable? El caso de Noticias afecta la página pública. Esta decisión no impide trabajar en `unifex_mejoras`.
2. Credenciales: antes de editar datos ya emitidos, definir si la corrección actualiza la persona de origen y cómo se muestran las versiones anteriores. Se propone que la persona sea la fuente de verdad y que toda reemisión quede registrada.
3. Actividad piloto distinta de FEXPO: nombre y reglas mínimas para validar que el diseño realmente es general.

-- Retira los datos de Noticias y Noches de FEXPO y los permisos de los
-- tres modulos descartados. Solo para ejecutar sobre la copia local
-- unifex_mejoras; fuera del patron V*.sql de migraciones automaticas.
-- No modifica la base de origen. Idempotente; sin CASCADE para detectar
-- dependencias nuevas antes de borrar informacion relacionada.
BEGIN;

DELETE FROM usuario_pantalla
 WHERE pantalla IN ('noticias', 'noches-fexpo', 'seguimiento-facultad');

DELETE FROM rol_pantalla
 WHERE pantalla IN ('noticias', 'noches-fexpo', 'seguimiento-facultad');

DROP TABLE IF EXISTS noticia;
DROP TABLE IF EXISTS noche_fexpo;

COMMIT;

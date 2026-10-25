BEGIN;

DELETE FROM usuario_pantalla
 WHERE pantalla IN ('noticias', 'noches-fexpo', 'seguimiento-facultad');

DELETE FROM rol_pantalla
 WHERE pantalla IN ('noticias', 'noches-fexpo', 'seguimiento-facultad');

DROP TABLE IF EXISTS noticia;
DROP TABLE IF EXISTS noche_fexpo;

COMMIT;

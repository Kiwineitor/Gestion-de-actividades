package com.usic.uniFex.model.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActividadService {

    public static final String TOPIC = "/topic/actividad";

    private final JdbcTemplate jdbc;
    private final SimpMessagingTemplate mensajeria;
    private final PlantillaCredencialService plantillas;
    private final FileStorageService archivos;
    private final AvisoPublicoService avisoPublico;

    private static final Set<String> TIPOS_IMAGEN = Set.of("image/png", "image/jpeg", "image/webp");

    public record Datos(String nombre, Integer anio, String tipo, Long copiarDe,
                        Boolean copiarPlano, Boolean copiarTextos,
                        String lugar, String fechasTexto, String descripcionPublica, String contacto,
                        Boolean publica, Boolean recibeInteresados) {
    }

    public record Resultado(boolean ok, String mensaje, Long id) {
        static Resultado error(String m) { return new Resultado(false, m, null); }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() {
        return jdbc.query("""
                SELECT e.id, e.nombre, e.anio, e.tipo, e.activa, e.plano_archivo IS NOT NULL AS con_plano,
                       e.lugar, e.fechas_texto, e.descripcion_publica, e.contacto, e.portada_archivo,
                       e.publica, e.recibe_interesados,
                       (SELECT count(*) FROM categoria c
                         WHERE c.id_edicion = e.id AND (c."_estado" IS NULL OR c."_estado" <> 'X')) AS categorias,
                       (SELECT count(*) FROM puesto p JOIN categoria c ON c.id = p.id_categoria
                         WHERE c.id_edicion = e.id AND (p."_estado" IS NULL OR p."_estado" <> 'X')) AS puestos,
                       (SELECT count(*) FROM puesto p JOIN categoria c ON c.id = p.id_categoria
                         WHERE c.id_edicion = e.id AND p.estado_puesto = 'O'
                           AND (p."_estado" IS NULL OR p."_estado" <> 'X')) AS vendidos,
                       (SELECT count(*) FROM inscripcion i
                         WHERE i.id_edicion = e.id AND (i."_estado" IS NULL OR i."_estado" <> 'X')) AS ventas
                  FROM edicion e
                 ORDER BY e.activa DESC, e.anio DESC, e.id DESC
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("nombre", rs.getString("nombre"));
            m.put("anio", rs.getInt("anio"));
            m.put("tipo", rs.getString("tipo"));
            m.put("activa", rs.getBoolean("activa"));
            m.put("conPlano", rs.getBoolean("con_plano"));
            m.put("categorias", rs.getLong("categorias"));
            m.put("puestos", rs.getLong("puestos"));
            m.put("vendidos", rs.getLong("vendidos"));
            m.put("ventas", rs.getLong("ventas"));
            m.put("lugar", rs.getString("lugar"));
            m.put("fechasTexto", rs.getString("fechas_texto"));
            m.put("descripcionPublica", rs.getString("descripcion_publica"));
            m.put("contacto", rs.getString("contacto"));
            String portada = rs.getString("portada_archivo");
            m.put("portadaUrl", portada == null ? null : "/files/" + portada);
            m.put("publica", rs.getBoolean("publica"));
            m.put("recibeInteresados", rs.getBoolean("recibe_interesados"));
            return m;
        });
    }

    @Transactional
    public Resultado crear(Datos d, Long usuarioId) {
        String nombre = limpio(d.nombre(), 120);
        if (nombre == null) return Resultado.error("La actividad necesita un nombre");
        int anio = d.anio() == null ? java.time.Year.now().getValue() : d.anio();
        if (anio < 2000 || anio > 2100) return Resultado.error("Año no válido");
        if (existeNombre(nombre, null)) return Resultado.error("Ya existe una actividad con ese nombre");
        if (d.copiarDe() != null && !existe(d.copiarDe())) return Resultado.error("La actividad de origen no existe");

        Long id = jdbc.queryForObject(
                "INSERT INTO edicion (nombre, anio, tipo, activa, plano_version) VALUES (?, ?, ?, FALSE, 0) RETURNING id",
                Long.class, nombre, anio, limpio(d.tipo(), 40));
        guardarPublico(id, d);

        if (d.copiarDe() != null) {
            int categorias = copiarEstructura(d.copiarDe(), id, usuarioId);
            if (Boolean.TRUE.equals(d.copiarPlano())) copiarPlano(d.copiarDe(), id);
            if (Boolean.TRUE.equals(d.copiarTextos())) copiarTextos(d.copiarDe(), id, usuarioId);
            log.info("Actividad {} creada copiando {} categoria(s) de la actividad {}", id, categorias, d.copiarDe());
        }
        return new Resultado(true, "Actividad creada", id);
    }

    @Transactional
    public Resultado editar(Long id, Datos d) {
        if (!existe(id)) return Resultado.error("La actividad no existe");
        String nombre = limpio(d.nombre(), 120);
        if (nombre == null) return Resultado.error("La actividad necesita un nombre");
        if (existeNombre(nombre, id)) return Resultado.error("Ya existe una actividad con ese nombre");
        Integer anio = d.anio();
        if (anio != null && (anio < 2000 || anio > 2100)) return Resultado.error("Año no válido");
        jdbc.update("UPDATE edicion SET nombre = ?, anio = COALESCE(?, anio), tipo = ? WHERE id = ?",
                nombre, anio, limpio(d.tipo(), 40), id);
        guardarPublico(id, d);
        return new Resultado(true, "Actividad actualizada", id);
    }

    @Transactional
    public Resultado activar(Long id) {
        if (!existe(id)) return Resultado.error("La actividad no existe");
        Long activa = activaActual();
        if (id.equals(activa)) return new Resultado(true, "Esa actividad ya está activa", id);
        Integer enTramite = jdbc.queryForObject("""
                SELECT count(*) FROM puesto p JOIN categoria c ON c.id = p.id_categoria
                 WHERE p.estado_puesto = 'T' AND c.id_edicion = ?
                """, Integer.class, activa);
        if (enTramite != null && enTramite > 0) {
            return Resultado.error("Hay " + enTramite + " puesto(s) en proceso de venta en la actividad actual. "
                    + "Espera a que se completen o venzan antes de cambiar de actividad.");
        }
        jdbc.update("UPDATE edicion SET activa = FALSE WHERE activa");
        jdbc.update("UPDATE edicion SET activa = TRUE WHERE id = ?", id);
        String nombre = jdbc.queryForObject("SELECT nombre FROM edicion WHERE id = ?", String.class, id);
        log.warn("Actividad activa cambiada de {} a {} ({})", activa, id, nombre);
        avisoPublico.cambio("actividad", id);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    plantillas.recargar();
                } catch (RuntimeException e) {
                    log.warn("No se pudieron recargar las plantillas de credencial: {}", e.getMessage());
                }
                try {
                    mensajeria.convertAndSend(TOPIC, Map.of("id", id, "nombre", nombre));
                } catch (RuntimeException e) {
                    log.warn("No se pudo avisar el cambio de actividad: {}", e.getMessage());
                }
            }
        });
        return new Resultado(true, "Ahora la actividad activa es " + nombre, id);
    }

    @Transactional
    public Resultado subirPortada(Long id, MultipartFile archivo) throws IOException {
        if (!existe(id)) return Resultado.error("La actividad no existe");
        if (archivo == null || archivo.isEmpty() || !TIPOS_IMAGEN.contains(String.valueOf(archivo.getContentType()))) {
            return Resultado.error("Sube una imagen PNG, JPG o WEBP");
        }
        if (archivo.getSize() > 8L * 1024 * 1024) return Resultado.error("La portada no puede pasar de 8 MB");
        if (!"image/webp".equals(archivo.getContentType())
                && ImageIO.read(new ByteArrayInputStream(archivo.getBytes())) == null) {
            return Resultado.error("La imagen no se puede leer");
        }
        String ruta = archivos.save(archivo, FileStorageService.Bucket.ACTIVIDADES, "portada");
        jdbc.update("UPDATE edicion SET portada_archivo = ? WHERE id = ?", ruta, id);
        avisoPublico.cambio("actividad", id);
        return new Resultado(true, "Portada actualizada", id);
    }

    @Transactional
    public Resultado quitarPortada(Long id) {
        if (!existe(id)) return Resultado.error("La actividad no existe");
        jdbc.update("UPDATE edicion SET portada_archivo = NULL WHERE id = ?", id);
        avisoPublico.cambio("actividad", id);
        return new Resultado(true, "Portada quitada", id);
    }

    private void guardarPublico(Long id, Datos d) {
        jdbc.update("""
                UPDATE edicion
                   SET lugar = ?, fechas_texto = ?, descripcion_publica = ?, contacto = ?,
                       publica = COALESCE(?, publica), recibe_interesados = COALESCE(?, recibe_interesados)
                 WHERE id = ?
                """, limpio(d.lugar(), 200), limpio(d.fechasTexto(), 120), texto(d.descripcionPublica(), 3000),
                limpio(d.contacto(), 120), d.publica(), d.recibeInteresados(), id);
        avisoPublico.cambio("actividad", id);
    }

    private static String texto(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private int copiarEstructura(Long origen, Long destino, Long usuarioId) {
        List<Long> categorias = jdbc.queryForList("""
                SELECT id FROM categoria
                 WHERE id_edicion = ? AND ("_estado" IS NULL OR "_estado" <> 'X') ORDER BY id
                """, Long.class, origen);
        for (Long cat : categorias) {
            Long nueva = jdbc.queryForObject("""
                    INSERT INTO categoria (nombre, descripcion, color, forma, tamano_mapa, mapa_proporcion,
                                           precio_base, id_edicion, "_estado", "_fecha_registro", "_registro_id_usuario")
                    SELECT nombre, descripcion, color, forma, tamano_mapa, mapa_proporcion,
                           precio_base, ?, 'A', now(), ?
                      FROM categoria WHERE id = ?
                    RETURNING id
                    """, Long.class, destino, usuarioId, cat);
            jdbc.update("""
                    INSERT INTO categoria_opcion (id_categoria, nombre, precio, predeterminada, orden,
                                                  "_fecha_registro", "_registro_id_usuario", "_estado")
                    SELECT ?, nombre, precio, predeterminada, orden, now(), ?, 'A'
                      FROM categoria_opcion
                     WHERE id_categoria = ? AND ("_estado" IS NULL OR "_estado" <> 'X')
                    """, nueva, usuarioId, cat);
            jdbc.update("""
                    INSERT INTO puesto (id_categoria, codigo, tamano, estado_puesto, version, mapa_x, mapa_y,
                                        mapa_escala, mapa_rotacion, referencia, precio, observacion,
                                        "_estado", "_fecha_registro", "_registro_id_usuario")
                    SELECT ?, codigo, tamano, CASE WHEN estado_puesto = 'X' THEN 'X' ELSE 'L' END, 0,
                           mapa_x, mapa_y, mapa_escala, mapa_rotacion, referencia, precio, observacion,
                           'A', now(), ?
                      FROM puesto
                     WHERE id_categoria = ? AND ("_estado" IS NULL OR "_estado" <> 'X')
                    """, nueva, usuarioId, cat);
        }
        return categorias.size();
    }

    private void copiarPlano(Long origen, Long destino) {
        jdbc.update("""
                UPDATE edicion d
                   SET plano_archivo = o.plano_archivo, plano_ancho = o.plano_ancho,
                       plano_alto = o.plano_alto, plano_version = o.plano_version, plano_subido_en = o.plano_subido_en
                  FROM edicion o
                 WHERE d.id = ? AND o.id = ?
                """, destino, origen);
    }

    private void copiarTextos(Long origen, Long destino, Long usuarioId) {
        jdbc.update("""
                INSERT INTO plano_texto (id_edicion, contenido, mapa_x, mapa_y, tamano, color, color_borde,
                                         grosor_borde, rotacion, "_fecha_registro", "_registro_id_usuario", "_estado")
                SELECT ?, contenido, mapa_x, mapa_y, tamano, color, color_borde, grosor_borde, rotacion,
                       now(), ?, "_estado"
                  FROM plano_texto
                 WHERE id_edicion = ? AND ("_estado" IS NULL OR "_estado" <> 'X')
                """, destino, usuarioId, origen);
    }

    private Long activaActual() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM edicion WHERE activa LIMIT 1", Long.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private boolean existe(Long id) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM edicion WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    private boolean existeNombre(String nombre, Long excepto) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM edicion WHERE lower(nombre) = lower(?) AND id <> ?",
                Integer.class, nombre, excepto == null ? -1L : excepto);
        return n != null && n > 0;
    }

    private static String limpio(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.length() > max ? t.substring(0, max) : t;
    }
}

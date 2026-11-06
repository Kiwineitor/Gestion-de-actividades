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
    private final CategoriaMapaService categoriaMapa;
    private final CategoriaOpcionService categoriaOpcion;

    private static final Set<String> TIPOS_IMAGEN = Set.of("image/png", "image/jpeg", "image/webp");

    public record Datos(String nombre, Integer anio, String tipo, Long copiarDe,
                        Boolean copiarPlano, Boolean copiarTextos,
                        String lugar, String fechasTexto, String descripcionPublica, String contacto,
                        Boolean publica, Boolean recibeInteresados,
                        String pagoBanco, String pagoCuenta, String pagoTitular, String pagoInstrucciones,
                        String pagoOficina, List<CategoriaMapaService.NuevaCategoria> categorias) {
    }

    public record Resultado(boolean ok, String mensaje, Long id) {
        static Resultado error(String m) { return new Resultado(false, m, null); }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar() {
        return jdbc.query("""
                SELECT e.id, e.nombre, e.anio, e.tipo, e.activa, e.plano_archivo IS NOT NULL AS con_plano,
                       e.lugar, e.fechas_texto, e.descripcion_publica, e.contacto, e.portada_archivo,
                       e.publica, e.recibe_interesados, e.publicacion_independiente, e.pago_qr_archivo, e.pago_banco, e.pago_cuenta,
                       e.pago_titular, e.pago_instrucciones, e.pago_oficina,
                       e.tipo_espacio, e.etiqueta_espacio, e.vende_espacios, e.vende_entradas, e.configuracion_paso, e.finalizada,
                       (SELECT min(d.fecha) FROM edicion_dia d WHERE d.id_edicion = e.id) AS primer_dia,
                       (SELECT max(d.fecha) FROM edicion_dia d WHERE d.id_edicion = e.id) AS ultimo_dia,
                       (SELECT count(*) FROM entrada_tipo t WHERE t.id_edicion = e.id AND t."_estado" <> 'X') AS tipos_entrada,
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
            m.put("publicada", rs.getBoolean("publica") && (rs.getBoolean("activa") || rs.getBoolean("publicacion_independiente")));
            m.put("recibeInteresados", rs.getBoolean("recibe_interesados"));
            String qr = rs.getString("pago_qr_archivo");
            m.put("pagoQrUrl", qr == null ? null : "/files/" + qr);
            m.put("pagoBanco", rs.getString("pago_banco"));
            m.put("pagoCuenta", rs.getString("pago_cuenta"));
            m.put("pagoTitular", rs.getString("pago_titular"));
            m.put("pagoInstrucciones", rs.getString("pago_instrucciones"));
            m.put("pagoOficina", rs.getString("pago_oficina"));
            m.put("tipoEspacio", rs.getString("tipo_espacio"));
            m.put("etiquetaEspacio", rs.getString("etiqueta_espacio"));
            m.put("vendeEspacios", rs.getBoolean("vende_espacios"));
            m.put("vendeEntradas", rs.getBoolean("vende_entradas"));
            m.put("configuracionPaso", rs.getInt("configuracion_paso"));
            m.put("finalizada", rs.getBoolean("finalizada"));
            m.put("primerDia", rs.getString("primer_dia"));
            m.put("ultimoDia", rs.getString("ultimo_dia"));
            m.put("tiposEntrada", rs.getLong("tipos_entrada"));
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

        if (d.categorias() != null && (d.categorias().size() > 30 || d.categorias().stream().anyMatch(c ->
                c == null || limpio(c.nombre(), 100) == null || c.cantidad() == null || c.cantidad() < 1 || c.cantidad() > 500
                        || c.precioBase() == null || c.precioBase().signum() < 0))) {
            return Resultado.error("Revisa los nombres, cantidades (1 a 500) y precios de las categorías");
        }
        Long id = jdbc.queryForObject(
                "INSERT INTO edicion (nombre, anio, tipo, activa, plano_version, plano_sin_fondo) VALUES (?, ?, ?, FALSE, 0, TRUE) RETURNING id",
                Long.class, nombre, anio, limpio(d.tipo(), 40));
        guardarPublico(id, d);

        if (d.copiarDe() != null) {
            int categorias = copiarEstructura(d.copiarDe(), id, usuarioId);
            if (Boolean.TRUE.equals(d.copiarPlano())) copiarPlano(d.copiarDe(), id);
            if (Boolean.TRUE.equals(d.copiarTextos())) copiarTextos(d.copiarDe(), id, usuarioId);
            log.info("Actividad {} creada copiando {} categoria(s) de la actividad {}", id, categorias, d.copiarDe());
        }
        if (d.categorias() != null) {
            for (var c : d.categorias()) {
                var guardada = categoriaMapa.crear(c, usuarioId, id);
                categoriaOpcion.asegurarPredeterminada(guardada.getId(), guardada.getNombre(), guardada.getPrecioBase(), usuarioId);
            }
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
    public Resultado subirQrPago(Long id, MultipartFile archivo) throws IOException {
        if (!existe(id)) return Resultado.error("La actividad no existe");
        if (archivo == null || archivo.isEmpty() || !TIPOS_IMAGEN.contains(String.valueOf(archivo.getContentType()))) {
            return Resultado.error("Sube una imagen PNG, JPG o WEBP");
        }
        if (archivo.getSize() > 5L * 1024 * 1024) return Resultado.error("El QR no puede pasar de 5 MB");
        String ruta = archivos.save(archivo, FileStorageService.Bucket.ACTIVIDADES, "qr-pago");
        jdbc.update("UPDATE edicion SET pago_qr_archivo = ? WHERE id = ?", ruta, id);
        avisoPublico.cambio("actividad", id);
        return new Resultado(true, "QR de pago actualizado", id);
    }

    @Transactional
    public Resultado quitarQrPago(Long id) {
        if (!existe(id)) return Resultado.error("La actividad no existe");
        jdbc.update("UPDATE edicion SET pago_qr_archivo = NULL WHERE id = ?", id);
        avisoPublico.cambio("actividad", id);
        return new Resultado(true, "QR de pago quitado", id);
    }

    @Transactional
    public Resultado habilitar(Long id, boolean habilitada) {
        if (!existe(id)) return Resultado.error("La actividad no existe");
        boolean conEspacios = Boolean.TRUE.equals(jdbc.queryForObject("SELECT vende_espacios FROM edicion WHERE id = ?", Boolean.class, id));
        if (habilitada && conEspacios && !id.equals(activaActual()) && !Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM puesto p JOIN categoria c ON c.id = p.id_categoria
                    WHERE c.id_edicion = ? AND p.mapa_x IS NOT NULL AND p.mapa_y IS NOT NULL
                      AND p."_estado" IS DISTINCT FROM 'X' AND c."_estado" IS DISTINCT FROM 'X')
                """, Boolean.class, id))) {
            return Resultado.error("Ubica al menos un espacio en el plano antes de publicar la actividad");
        }
        jdbc.update("UPDATE edicion SET publica = ?, publicacion_independiente = ? WHERE id = ?", habilitada, habilitada, id);
        avisoPublico.cambio("actividad", id);
        return new Resultado(true, habilitada ? "Actividad publicada en el portal"
                : "Actividad deshabilitada: oculta al público", id);
    }

    @Transactional
    public Resultado quitarPortada(Long id) {
        if (!existe(id)) return Resultado.error("La actividad no existe");
        jdbc.update("UPDATE edicion SET portada_archivo = NULL WHERE id = ?", id);
        avisoPublico.cambio("actividad", id);
        return new Resultado(true, "Portada quitada", id);
    }

    @Transactional
    public Resultado eliminar(Long id, Long usuarioId) {
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT nombre, activa FROM edicion WHERE id = ? FOR UPDATE", id);
        if (filas.isEmpty()) return Resultado.error("La actividad no existe");
        String nombre = (String) filas.get(0).get("nombre");
        if (Boolean.TRUE.equals(filas.get(0).get("activa"))) {
            return Resultado.error("«" + nombre + "» es la feria en curso. Pon otra en curso antes de eliminarla.");
        }
        Integer ventas = jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM inscripcion WHERE id_edicion = ?)
                     + (SELECT count(*) FROM venta_boleto WHERE id_edicion = ?)
                     + (SELECT count(*) FROM noticia WHERE id_edicion = ?)
                     + (SELECT count(*) FROM noche_fexpo WHERE id_edicion = ?)
                """, Integer.class, id, id, id, id);
        if (ventas != null && ventas > 0) {
            return Resultado.error("«" + nombre + "» tiene " + ventas + " venta(s) o registro(s) asociados. "
                    + "Para conservar el historial no se puede eliminar.");
        }
        String puestos = "SELECT p.id FROM puesto p JOIN categoria c ON c.id = p.id_categoria WHERE c.id_edicion = ?";
        String categorias = "SELECT id FROM categoria WHERE id_edicion = ?";
        jdbc.update("DELETE FROM notificacion WHERE id_puesto IN (" + puestos + ")", id);
        jdbc.update("DELETE FROM interesado_stand WHERE id_puesto IN (" + puestos + ") OR id_categoria IN (" + categorias + ")", id, id);
        jdbc.update("DELETE FROM vendedor_puesto WHERE id_puesto IN (" + puestos + ")", id);
        jdbc.update("DELETE FROM puesto_foto WHERE id_puesto IN (" + puestos + ")", id);
        jdbc.update("DELETE FROM puesto WHERE id_categoria IN (" + categorias + ")", id);
        jdbc.update("DELETE FROM categoria_opcion WHERE id_categoria IN (" + categorias + ")", id);
        jdbc.update("DELETE FROM categoria WHERE id_edicion = ?", id);
        jdbc.update("DELETE FROM plano_texto WHERE id_edicion = ?", id);
        jdbc.update("UPDATE plantilla_credencial SET id_edicion = NULL WHERE id_edicion = ?", id);
        jdbc.update("DELETE FROM edicion WHERE id = ?", id);
        log.warn("Actividad {} ({}) eliminada por el usuario {}", id, nombre, usuarioId);
        avisoPublico.cambio("actividad", id);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    plantillas.recargar();
                } catch (RuntimeException e) {
                    log.warn("No se pudieron recargar las plantillas de credencial: {}", e.getMessage());
                }
            }
        });
        return new Resultado(true, "Actividad «" + nombre + "» eliminada", id);
    }

    private void guardarPublico(Long id, Datos d) {
        jdbc.update("""
                UPDATE edicion
                   SET lugar = ?, fechas_texto = ?, descripcion_publica = ?, contacto = ?,
                       publica = COALESCE(?, publica), recibe_interesados = COALESCE(?, recibe_interesados),
                       pago_banco = ?, pago_cuenta = ?, pago_titular = ?, pago_instrucciones = ?, pago_oficina = ?
                 WHERE id = ?
                """, limpio(d.lugar(), 200), limpio(d.fechasTexto(), 120), texto(d.descripcionPublica(), 3000),
                limpio(d.contacto(), 120), d.publica(), d.recibeInteresados(),
                limpio(d.pagoBanco(), 80), limpio(d.pagoCuenta(), 60), limpio(d.pagoTitular(), 120),
                texto(d.pagoInstrucciones(), 500), texto(d.pagoOficina(), 300), id);
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
                       plano_alto = o.plano_alto, plano_version = o.plano_version, plano_subido_en = o.plano_subido_en,
                       plano_sin_fondo = o.plano_sin_fondo
                  FROM edicion o
                 WHERE d.id = ? AND o.id = ?
                """, destino, origen);
    }

    private void copiarTextos(Long origen, Long destino, Long usuarioId) {
        jdbc.update("""
                INSERT INTO plano_texto (id_edicion, contenido, mapa_x, mapa_y, tamano, color, color_borde,
                                         grosor_borde, rotacion, fondo, ancho, alto,
                                         "_fecha_registro", "_registro_id_usuario", "_estado")
                SELECT ?, contenido, mapa_x, mapa_y, tamano, color, color_borde, grosor_borde, rotacion, fondo, ancho, alto,
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

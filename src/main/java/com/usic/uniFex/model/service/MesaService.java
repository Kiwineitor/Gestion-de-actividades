package com.usic.uniFex.model.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class MesaService {

    public static final String TOPIC = "/topic/publico/mesas/";
    private static final Set<String> FORMAS = Set.of("circulo", "cuadrado", "rectangulo");
    private static final Set<String> TIPOS_IMAGEN = Set.of("image/png", "image/jpeg", "image/webp");
    private static final long VENTANA_INTENTOS_MS = 10 * 60 * 1000L;
    private static final long ATENCION_MS = 120_000L;
    private static final char[] ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM, HH:mm", Locale.of("es", "BO"));

    private final JdbcTemplate jdbc;
    private final SimpMessagingTemplate mensajeria;
    private final FileStorageService archivos;
    private final AvisoReservaMesaService avisos;
    private final AvisoPublicoService avisoPublico;
    private final MesaPaseService pases;
    private final ObjectMapper json;
    private final SecureRandom azar = new SecureRandom();
    private final Map<String, Deque<Long>> intentos = new ConcurrentHashMap<>();
    private final Map<Long, Atencion> atenciones = new ConcurrentHashMap<>();

    private record Atencion(String token, Long eventoId, long expira) {
        boolean vigente(long ahora) { return expira > ahora; }
    }

    @org.springframework.beans.factory.annotation.Value("${unifex.mesas.intentos-por-ip:30}")
    private int intentosPorIp = 30;

    public record EventoDatos(String nombre, String localNombre, String localDireccion, String descripcion,
                              String fechaInicio, String horaLimite, Integer minutosBloqueo, String pagoBanco,
                              String pagoCuenta, String pagoTitular, String pagoInstrucciones,
                              String celularContacto, String politicas, Boolean publicado, String tiposPase) {
    }

    public record MesaDatos(Long id, String codigo, String zona, Integer capacidad, BigDecimal precio,
                            String beneficios, String forma, String color, Double mapaX, Double mapaY,
                            Double tamano) {
    }

    public record Elemento(String id, String tipo, String texto, Double x, Double y, Double ancho, Double alto,
                           String color, String fondo, Double tamano, Double rotacion) {
    }

    public record Cliente(String nombre, String apellidos, String ci, String celular, String correo, Integer personas) {
    }

    public record Resultado(boolean ok, String mensaje, Map<String, Object> datos) {
        static Resultado error(String m) { return new Resultado(false, m, null); }
        static Resultado ok(String m, Map<String, Object> d) { return new Resultado(true, m, d); }
    }

    public static class Rechazo extends RuntimeException {
        public Rechazo(String mensaje) {
            super(mensaje);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listarEventos() {
        return jdbc.query("""
                SELECT e.*,
                       (SELECT count(*) FROM mesa m WHERE m.id_evento = e.id AND m."_estado" = 'A') AS total,
                       (SELECT count(*) FROM mesa m WHERE m.id_evento = e.id AND m."_estado" = 'A' AND m.estado = 'O') AS vendidas,
                       (SELECT count(*) FROM mesa_reserva r WHERE r.id_evento = e.id AND r.estado = 'EN_REVISION') AS por_validar,
                       (SELECT COALESCE(sum(r.monto), 0) FROM mesa_reserva r WHERE r.id_evento = e.id AND r.estado = 'CONFIRMADA') AS confirmado
                  FROM mesa_evento e
                 WHERE e."_estado" = 'A'
                 ORDER BY e.fecha_inicio DESC, e.id DESC
                """, (rs, i) -> {
            Map<String, Object> m = evento(rs, true);
            m.put("mesas", rs.getLong("total"));
            m.put("vendidas", rs.getLong("vendidas"));
            m.put("porValidar", rs.getLong("por_validar"));
            m.put("confirmado", rs.getBigDecimal("confirmado"));
            return m;
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detalleEvento(Long id) {
        List<Map<String, Object>> filas = jdbc.query("SELECT * FROM mesa_evento WHERE id = ? AND \"_estado\" = 'A'",
                (rs, i) -> evento(rs, true), id);
        if (filas.isEmpty()) return null;
        Map<String, Object> e = filas.get(0);
        e.put("mesas", mesas(id, true));
        return e;
    }

    @Transactional
    public Resultado crearEvento(EventoDatos d, Long usuarioId) {
        String error = validarEvento(d);
        if (error != null) return Resultado.error(error);
        Long id = jdbc.queryForObject("""
                INSERT INTO mesa_evento (nombre, local_nombre, local_direccion, descripcion, fecha_inicio, hora_limite,
                       minutos_bloqueo, pago_banco, pago_cuenta, pago_titular, pago_instrucciones, celular_contacto,
                       politicas, publicado, "_registro_id_usuario")
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, parametrosEvento(d, usuarioId));
        guardarTiposPase(id, d.tiposPase());
        avisoPublico.cambio("evento", id);
        return Resultado.ok("Evento creado", Map.of("id", id));
    }

    @Transactional
    public Resultado editarEvento(Long id, EventoDatos d, Long usuarioId) {
        if (!existeEvento(id)) return Resultado.error("El evento no existe");
        String error = validarEvento(d);
        if (error != null) return Resultado.error(error);
        Object[] p = parametrosEvento(d, usuarioId);
        Object[] conId = java.util.Arrays.copyOf(p, p.length + 1);
        conId[p.length] = id;
        jdbc.update("""
                UPDATE mesa_evento SET nombre = ?, local_nombre = ?, local_direccion = ?, descripcion = ?,
                       fecha_inicio = ?, hora_limite = ?, minutos_bloqueo = ?, pago_banco = ?, pago_cuenta = ?,
                       pago_titular = ?, pago_instrucciones = ?, celular_contacto = ?, politicas = ?, publicado = ?,
                       "_fecha_modificacion" = now(), "_modificacion_id_usuario" = ?
                 WHERE id = ?
                """, conId);
        guardarTiposPase(id, d.tiposPase());
        avisoPublico.cambio("evento", id);
        return Resultado.ok("Evento actualizado", Map.of("id", id));
    }

    private void guardarTiposPase(Long id, String tipos) {
        String t = tipos == null ? null : String.join("\n", tipos.lines().map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.length() > 60 ? s.substring(0, 60) : s).distinct().limit(12).toList());
        jdbc.update("UPDATE mesa_evento SET tipos_pase = ? WHERE id = ?", t == null || t.isEmpty() ? null : t, id);
    }

    @Transactional
    public Resultado publicar(Long id, boolean publicado, Long usuarioId) {
        int n = jdbc.update("UPDATE mesa_evento SET publicado = ?, \"_fecha_modificacion\" = now(), \"_modificacion_id_usuario\" = ? "
                + "WHERE id = ? AND \"_estado\" = 'A'", publicado, usuarioId, id);
        if (n == 0) return Resultado.error("El evento no existe");
        avisoPublico.cambio("evento", id);
        return Resultado.ok(publicado ? "Evento habilitado: ya recibe reservas" : "Evento deshabilitado: oculto al público", Map.of("id", id));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> reservasPendientes() {
        return jdbc.query(SELECT_RESERVA + """
                 JOIN mesa_evento ev ON ev.id = r.id_evento
                WHERE r.estado IN ('EN_REVISION', 'BLOQUEADA') AND ev."_estado" = 'A'
                ORDER BY CASE r.estado WHEN 'EN_REVISION' THEN 0 ELSE 1 END, COALESCE(r.comprobante_subido_en, r.creado_en)
                """, (rs, i) -> {
            Map<String, Object> m = reserva(rs, true);
            m.put("eventoId", rs.getLong("id_evento"));
            m.put("evento", rs.getString("evento_nombre"));
            m.put("tiposPase", tipos(rs.getString("evento_tipos")));
            return m;
        });
    }

    private static List<String> tipos(String texto) {
        return texto == null ? List.of() : texto.lines().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Transactional
    public Resultado subirImagenEvento(Long id, MultipartFile archivo, boolean esPlano) throws IOException {
        if (!existeEvento(id)) return Resultado.error("El evento no existe");
        if (archivo == null || archivo.isEmpty() || !TIPOS_IMAGEN.contains(String.valueOf(archivo.getContentType()))) {
            return Resultado.error("Sube una imagen PNG, JPG o WEBP");
        }
        var imagen = ImageIO.read(new ByteArrayInputStream(archivo.getBytes()));
        if (imagen == null && !"image/webp".equals(archivo.getContentType())) return Resultado.error("La imagen no se puede leer");
        String ruta = archivos.save(archivo, FileStorageService.Bucket.MESAS, esPlano ? "plano" : "qr-pago");
        if (esPlano) {
            jdbc.update("UPDATE mesa_evento SET plano_archivo = ?, plano_ancho = ?, plano_alto = ? WHERE id = ?",
                    ruta, imagen == null ? null : imagen.getWidth(), imagen == null ? null : imagen.getHeight(), id);
        } else {
            jdbc.update("UPDATE mesa_evento SET pago_qr_archivo = ? WHERE id = ?", ruta, id);
        }
        avisoPublico.cambio("evento", id);
        return Resultado.ok(esPlano ? "Plano actualizado" : "QR de pago actualizado", Map.of("url", "/files/" + ruta));
    }

    @Transactional
    public Resultado subirLogo(Long id, MultipartFile archivo) throws IOException {
        if (!existeEvento(id)) return Resultado.error("El evento no existe");
        String tipo = String.valueOf(archivo == null ? "" : archivo.getContentType());
        if (archivo == null || archivo.isEmpty() || !(tipo.equals("image/png") || tipo.equals("image/jpeg"))) {
            return Resultado.error("El logo debe ser una imagen PNG o JPG");
        }
        if (archivo.getSize() > 5L * 1024 * 1024) return Resultado.error("El logo no puede pasar de 5 MB");
        if (ImageIO.read(new ByteArrayInputStream(archivo.getBytes())) == null) return Resultado.error("La imagen no se puede leer");
        String ruta = archivos.save(archivo, FileStorageService.Bucket.MESAS, "logo");
        jdbc.update("UPDATE mesa_evento SET logo_archivo = ? WHERE id = ?", ruta, id);
        avisoPublico.cambio("evento", id);
        return Resultado.ok("Logo del local actualizado", Map.of("url", "/files/" + ruta));
    }

    @Transactional
    public Resultado quitarLogo(Long id) {
        if (!existeEvento(id)) return Resultado.error("El evento no existe");
        jdbc.update("UPDATE mesa_evento SET logo_archivo = NULL WHERE id = ?", id);
        avisoPublico.cambio("evento", id);
        return Resultado.ok("Logo quitado", Map.of());
    }

    @Transactional
    public Resultado quitarPlano(Long id) {
        if (!existeEvento(id)) return Resultado.error("El evento no existe");
        jdbc.update("UPDATE mesa_evento SET plano_archivo = NULL WHERE id = ?", id);
        avisoPublico.cambio("evento", id);
        return Resultado.ok("Fondo quitado", Map.of());
    }

    @Transactional
    public Resultado cambiarForma(Long id, Integer ancho, Integer alto, boolean conservar) {
        if (!existeEvento(id)) return Resultado.error("El evento no existe");
        if (ancho == null || alto == null || ancho < 100 || alto < 100 || ancho > 10000 || alto > 10000
                || alto > ancho * 5 || ancho > alto * 5) {
            return Resultado.error("Esa forma no es válida");
        }
        Map<String, Object> e = jdbc.queryForMap(
                "SELECT plano_archivo, plano_ancho, plano_alto, plano_elementos FROM mesa_evento WHERE id = ?", id);
        if (e.get("plano_archivo") != null) {
            return Resultado.error("La forma la da la imagen de fondo. Quita el fondo para elegir otra forma.");
        }
        Number w = (Number) e.get("plano_ancho");
        Number h = (Number) e.get("plano_alto");
        double a1 = w != null && h != null && w.doubleValue() > 0 ? h.doubleValue() / w.doubleValue() : 0.62;
        double a2 = (double) alto / ancho;
        if (conservar) {
            double s = Math.min(1, a2 / a1);
            double k = s * a1 / a2;
            jdbc.update("""
                    UPDATE mesa SET mapa_x = 0.5 + (mapa_x - 0.5) * ?, mapa_y = 0.5 + (mapa_y - 0.5) * ?,
                           tamano = tamano * ?, version = version + 1
                     WHERE id_evento = ? AND "_estado" = 'A' AND mapa_x IS NOT NULL AND mapa_y IS NOT NULL
                    """, s, k, s, id);
            List<Elemento> movidos = elementos((String) e.get("plano_elementos")).stream().map(el -> {
                double an = el.ancho() == null ? 0.2 : el.ancho();
                double al = el.alto() == null ? 0.08 : el.alto();
                double cx = 0.5 + ((el.x() == null ? 0 : el.x()) + an / 2 - 0.5) * s;
                double cy = 0.5 + ((el.y() == null ? 0 : el.y()) + al / 2 - 0.5) * k;
                return new Elemento(el.id(), el.tipo(), el.texto(), cx - an * s / 2, cy - al * k / 2, an * s, al * k,
                        el.color(), el.fondo(), el.tamano() == null ? null : el.tamano() * s, el.rotacion());
            }).toList();
            try {
                jdbc.update("UPDATE mesa_evento SET plano_elementos = ? WHERE id = ?", json.writeValueAsString(movidos), id);
            } catch (JsonProcessingException ex) {
                return Resultado.error("No se pudieron acomodar los textos del plano");
            }
        }
        jdbc.update("UPDATE mesa_evento SET plano_ancho = ?, plano_alto = ? WHERE id = ?", ancho, alto, id);
        avisoPublico.cambio("evento", id);
        return Resultado.ok("Forma del plano actualizada", Map.of());
    }

    @Transactional
    public Resultado quitarQrPago(Long id) {
        if (!existeEvento(id)) return Resultado.error("El evento no existe");
        jdbc.update("UPDATE mesa_evento SET pago_qr_archivo = NULL WHERE id = ?", id);
        avisoPublico.cambio("evento", id);
        return Resultado.ok("QR de pago quitado", Map.of());
    }

    @Transactional
    public Resultado guardarElementos(Long eventoId, List<Elemento> lista) {
        if (!existeEvento(eventoId)) return Resultado.error("El evento no existe");
        if (lista == null) return Resultado.error("No llegaron elementos");
        if (lista.size() > 200) return Resultado.error("El plano admite hasta 200 textos y zonas");
        List<Elemento> limpios = lista.stream().map(e -> new Elemento(
                idElemento(e.id()),
                "zona".equals(e.tipo()) ? "zona" : "texto",
                e.texto() == null ? "" : (e.texto().length() > 80 ? e.texto().substring(0, 80) : e.texto()),
                acotar(e.x()), acotar(e.y()),
                e.ancho() == null ? 0.2 : Math.max(0.01, Math.min(1, e.ancho())),
                e.alto() == null ? 0.08 : Math.max(0.01, Math.min(1, e.alto())),
                color(e.color()), color(e.fondo()),
                e.tamano() == null ? 0.025 : Math.max(0.006, Math.min(0.2, e.tamano())),
                e.rotacion() == null ? 0 : Math.max(-180, Math.min(180, e.rotacion()))))
                .toList();
        try {
            jdbc.update("UPDATE mesa_evento SET plano_elementos = ? WHERE id = ?", json.writeValueAsString(limpios), eventoId);
        } catch (JsonProcessingException e) {
            return Resultado.error("No se pudieron guardar los textos del plano");
        }
        avisoPublico.cambio("evento", eventoId);
        return Resultado.ok("Textos y zonas guardados", Map.of("elementos", limpios));
    }

    private static String idElemento(String id) {
        String limpio = id == null ? "" : id.replaceAll("[^A-Za-z0-9_-]", "");
        return limpio.length() > 40 ? limpio.substring(0, 40) : limpio;
    }

    private List<Elemento> elementos(String texto) {
        if (texto == null || texto.isBlank()) return List.of();
        try {
            return List.of(json.readValue(texto, Elemento[].class));
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    @Transactional
    public Resultado guardarMesas(Long eventoId, List<MesaDatos> lista, Long usuarioId) {
        if (!existeEvento(eventoId)) return Resultado.error("El evento no existe");
        if (lista == null) return Resultado.error("No llegaron mesas");
        Set<String> codigos = new HashSet<>();
        for (MesaDatos m : lista) {
            String codigo = limpio(m.codigo(), 20);
            if (codigo == null) return Resultado.error("Cada mesa necesita un número o código");
            if (!codigos.add(codigo.toUpperCase())) return Resultado.error("El código " + codigo + " está repetido");
            if (m.precio() != null && m.precio().signum() < 0) return Resultado.error("Precio inválido en la mesa " + codigo);
        }
        List<Long> tocadas = new ArrayList<>();
        for (MesaDatos m : lista) {
            String forma = m.forma() != null && FORMAS.contains(m.forma()) ? m.forma() : "circulo";
            double tamano = m.tamano() == null ? 0.06 : Math.max(0.015, Math.min(0.25, m.tamano()));
            Object[] valores = { limpio(m.codigo(), 20), limpio(m.zona(), 60),
                    m.capacidad() == null ? 4 : Math.max(1, Math.min(50, m.capacidad())),
                    m.precio() == null ? BigDecimal.ZERO : m.precio(), limpio(m.beneficios(), 300), forma,
                    color(m.color()), acotar(m.mapaX()), acotar(m.mapaY()), tamano };
            if (m.id() == null) {
                Long id = jdbc.queryForObject("""
                        INSERT INTO mesa (id_evento, codigo, zona, capacidad, precio, beneficios, forma, color,
                                          mapa_x, mapa_y, tamano, "_registro_id_usuario")
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                        """, Long.class, concat(new Object[] { eventoId }, valores, new Object[] { usuarioId }));
                tocadas.add(id);
            } else {
                int n = jdbc.update("""
                        UPDATE mesa SET codigo = ?, zona = ?, capacidad = ?, precio = ?, beneficios = ?, forma = ?,
                               color = ?, mapa_x = ?, mapa_y = ?, tamano = ?, version = version + 1,
                               "_fecha_modificacion" = now(), "_modificacion_id_usuario" = ?
                         WHERE id = ? AND id_evento = ? AND "_estado" = 'A'
                        """, concat(valores, new Object[] { usuarioId, m.id(), eventoId }));
                if (n > 0) tocadas.add(m.id());
            }
        }
        publicarDespues(eventoId, tocadas);
        return Resultado.ok("Mesas guardadas", Map.of("mesas", mesas(eventoId, true)));
    }

    @Transactional
    public Resultado eliminarMesa(Long mesaId, Long usuarioId) {
        Long evento = eventoDeMesa(mesaId);
        int n = jdbc.update("""
                UPDATE mesa SET "_estado" = 'X', version = version + 1, "_fecha_modificacion" = now(),
                       "_modificacion_id_usuario" = ?
                 WHERE id = ? AND "_estado" = 'A' AND estado IN ('L', 'X')
                """, usuarioId, mesaId);
        if (n == 0) return Resultado.error("Solo se elimina una mesa libre o fuera de venta");
        publicarDespues(evento, List.of(mesaId));
        return Resultado.ok("Mesa eliminada", Map.of());
    }

    @Transactional
    public Resultado fueraDeVenta(Long mesaId, boolean bloquear, Long usuarioId) {
        Long evento = eventoDeMesa(mesaId);
        int n = jdbc.update("UPDATE mesa SET estado = ?, version = version + 1, \"_modificacion_id_usuario\" = ? "
                + "WHERE id = ? AND \"_estado\" = 'A' AND estado = ?",
                bloquear ? "X" : "L", usuarioId, mesaId, bloquear ? "L" : "X");
        if (n == 0) return Resultado.error(bloquear ? "Solo se saca de venta una mesa libre" : "La mesa no está fuera de venta");
        publicarDespues(evento, List.of(mesaId));
        return Resultado.ok(bloquear ? "Mesa fuera de venta" : "Mesa disponible otra vez", Map.of());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> eventoPublico(Long id) {
        List<Map<String, Object>> filas = jdbc.query("""
                SELECT * FROM mesa_evento WHERE id = ? AND "_estado" = 'A' AND publicado
                """, (rs, i) -> evento(rs, false), id);
        if (filas.isEmpty()) return null;
        Map<String, Object> e = filas.get(0);
        e.put("mesas", mesas(id, false));
        return e;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> eventosPublicos() {
        return jdbc.query("""
                SELECT e.*,
                       (SELECT count(*) FROM mesa m WHERE m.id_evento = e.id AND m."_estado" = 'A') AS total,
                       (SELECT count(*) FROM mesa m WHERE m.id_evento = e.id AND m."_estado" = 'A' AND m.estado = 'L') AS libres,
                       (SELECT min(m.precio) FROM mesa m WHERE m.id_evento = e.id AND m."_estado" = 'A' AND m.estado <> 'X') AS desde
                  FROM mesa_evento e
                 WHERE e."_estado" = 'A' AND e.publicado AND e.fecha_inicio > now() - interval '12 hours'
                 ORDER BY e.fecha_inicio
                """, (rs, i) -> {
            Map<String, Object> m = evento(rs, false);
            m.put("mesas", rs.getLong("total"));
            m.put("libres", rs.getLong("libres"));
            m.put("desde", rs.getBigDecimal("desde"));
            return m;
        });
    }

    public Resultado tomarAtencion(Long eventoId, Long mesaId, String token) {
        if (token == null || token.isBlank() || token.length() > 80) return Resultado.error("Sesión no válida. Recarga la página.");
        if (eventoPublico(eventoId) == null) return Resultado.error("El evento no está disponible");
        List<String> estado = jdbc.queryForList("SELECT estado FROM mesa WHERE id = ? AND id_evento = ? AND \"_estado\" = 'A'",
                String.class, mesaId, eventoId);
        if (estado.isEmpty()) return Resultado.error("La mesa no existe");
        if (!"L".equals(estado.get(0))) return Resultado.error("Esta mesa ya no está disponible. Elige otra.");
        long ahora = System.currentTimeMillis();
        boolean[] nueva = { false };
        Atencion vigente = atenciones.compute(mesaId, (k, actual) -> {
            if (actual != null && actual.vigente(ahora) && !actual.token().equals(token)) return actual;
            nueva[0] = actual == null || !actual.vigente(ahora);
            return new Atencion(token, eventoId, ahora + ATENCION_MS);
        });
        if (!vigente.token().equals(token)) {
            return Resultado.error("Otra persona está comprando esta mesa en este momento. Elige otra o espera un momento.");
        }
        for (Map.Entry<Long, Atencion> e : atenciones.entrySet()) {
            if (!e.getKey().equals(mesaId) && e.getValue().token().equals(token) && atenciones.remove(e.getKey(), e.getValue())) {
                publicarDespues(e.getValue().eventoId(), List.of(e.getKey()));
            }
        }
        if (nueva[0]) publicarDespues(eventoId, List.of(mesaId));
        return Resultado.ok("Mesa apartada mientras completas tus datos", Map.of("segundos", ATENCION_MS / 1000));
    }

    public Resultado soltarAtencion(Long mesaId, String token) {
        Atencion a = atenciones.get(mesaId);
        if (a == null || token == null || !a.token().equals(token)) return Resultado.ok("Sin cambios", Map.of());
        if (atenciones.remove(mesaId, a)) publicarDespues(a.eventoId(), List.of(mesaId));
        return Resultado.ok("Mesa liberada", Map.of());
    }

    private boolean enAtencion(Long mesaId) {
        Atencion a = atenciones.get(mesaId);
        return a != null && a.vigente(System.currentTimeMillis());
    }

    @Transactional
    public Resultado reservar(Long eventoId, Long mesaId, Cliente c, String ip) {
        return reservar(eventoId, List.of(mesaId), c, ip);
    }

    @Transactional
    public Resultado reservar(Long eventoId, Long mesaId, Cliente c, String ip, String token) {
        return reservar(eventoId, mesaId, c, ip);
    }

    @Transactional
    public Resultado reservar(Long eventoId, List<Long> ids, Cliente c, String ip) {
        if (ids == null || ids.isEmpty() || ids.size() > 2 || ids.stream().anyMatch(java.util.Objects::isNull)
                || ids.stream().distinct().count() != ids.size()) {
            return Resultado.error("Elige una o dos mesas distintas");
        }
        String error = validarCliente(c);
        if (error != null) return Resultado.error(error);
        if (!permitirIntento(ip)) return Resultado.error("Demasiados intentos seguidos. Espera unos minutos y vuelve a intentar.");
        // Serializa el límite por CI o celular, incluso cuando las mesas solicitadas son distintas.
        List<Integer> plazos = jdbc.queryForList("""
                SELECT minutos_bloqueo FROM mesa_evento
                 WHERE id = ? AND publicado AND "_estado" = 'A' AND pago_qr_archivo IS NOT NULL
                 FOR NO KEY UPDATE
                """, Integer.class, eventoId);
        if (plazos.isEmpty()) return Resultado.error("El evento no tiene habilitado el pago por QR");
        String ci = c.ci().trim().toUpperCase(Locale.ROOT);
        String celular = soloDigitos(c.celular());
        Integer activas = jdbc.queryForObject("""
                SELECT COALESCE(sum(CASE WHEN id_mesa_adicional IS NULL THEN 1 ELSE 2 END), 0)::integer
                  FROM mesa_reserva
                 WHERE id_evento = ? AND (upper(ci) = ? OR celular = ?)
                   AND (estado = 'EN_REVISION' OR (estado = 'BLOQUEADA' AND bloqueo_expira > clock_timestamp()))
                """, Integer.class, eventoId, ci, celular);
        if (activas + ids.size() > 2) {
            return Resultado.error("Puedes tener hasta dos mesas pendientes por evento. Espera la validación del pago o cancela una reserva pendiente.");
        }
        List<Map<String, Object>> elegidas = jdbc.queryForList("""
                SELECT id, precio, capacidad FROM mesa
                 WHERE id = ANY(?) AND id_evento = ? AND "_estado" = 'A' AND estado = 'L'
                 ORDER BY id FOR UPDATE
                """, (Object) ids.toArray(new Long[0]), eventoId);
        if (elegidas.size() != ids.size()) return Resultado.error("Alguna mesa seleccionada ya no está disponible. Revisa el plano.");
        BigDecimal monto = elegidas.stream().map(m -> (BigDecimal) m.get("precio")).reduce(BigDecimal.ZERO, BigDecimal::add);
        int capacidad = elegidas.stream().mapToInt(m -> (Integer) m.get("capacidad")).sum();
        Long adicional = ids.size() == 2 ? ids.get(1) : null;
        Map<String, Object> datosAdicional = elegidas.stream().filter(m -> m.get("id").equals(adicional))
                .findFirst().orElse(Map.of());
        String codigo = nuevoCodigo();
        Long reservaId = jdbc.queryForObject("""
                INSERT INTO mesa_reserva (id_evento, id_mesa, id_mesa_adicional, codigo, nombre, apellidos, ci, celular,
                                          correo, monto, estado, bloqueo_expira, ip, personas, retiro_fisico,
                                          monto_mesa_adicional, manillas_mesa_adicional)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'BLOQUEADA', clock_timestamp() + (? * interval '1 minute'),
                        ?, ?, TRUE, ?, ?) RETURNING id
                """, Long.class, eventoId, ids.getFirst(), adicional, codigo, limpio(c.nombre(), 80),
                java.util.Objects.toString(limpio(c.apellidos(), 80), ""), ci, celular, limpio(c.correo(), 120),
                monto, plazos.getFirst(), ip, capacidad, datosAdicional.get("precio"), datosAdicional.get("capacidad"));
        int n = jdbc.update("""
                UPDATE mesa SET estado = 'T', id_reserva = ?,
                       bloqueo_expira = (SELECT bloqueo_expira FROM mesa_reserva WHERE id = ?), version = version + 1
                 WHERE id = ANY(?) AND id_evento = ? AND estado = 'L' AND "_estado" = 'A'
                """, reservaId, reservaId, (Object) ids.toArray(new Long[0]), eventoId);
        if (n != ids.size()) throw new Rechazo("Alguna mesa seleccionada ya no está disponible. Revisa el plano.");
        publicarDespues(eventoId, ids);
        avisos.avisar(reservaId, AvisoReservaMesaService.Tipo.BLOQUEADA);
        return Resultado.ok("Mesas apartadas para pagar", Map.of("codigo", codigo));
    }

    @Transactional(rollbackFor = IOException.class)
    public Resultado adjuntarComprobante(String codigo, MultipartFile archivo) throws IOException {
        Map<String, Object> r = reservaInterna(codigo);
        if (r == null) return Resultado.error("La reserva no existe");
        if (!"BLOQUEADA".equals(r.get("estado"))) return Resultado.error("Esta reserva ya no espera comprobante");
        String tipo = String.valueOf(archivo == null ? "" : archivo.getContentType());
        if (archivo == null || archivo.isEmpty() || !(TIPOS_IMAGEN.contains(tipo) || "application/pdf".equals(tipo))) {
            return Resultado.error("Adjunta una foto (PNG, JPG, WEBP) o un PDF del comprobante");
        }
        if (archivo.getSize() > 10L * 1024 * 1024) return Resultado.error("El comprobante no puede pasar de 10 MB");
        Long reservaId = (Long) r.get("id");
        int n = jdbc.update("""
                UPDATE mesa SET estado = 'R', bloqueo_expira = NULL, version = version + 1
                 WHERE id_reserva = ? AND estado = 'T' AND bloqueo_expira > clock_timestamp()
                """, reservaId);
        if (n != idsDeReserva(r).size()) throw new Rechazo("El tiempo para adjuntar el comprobante venció");
        String ruta = archivos.save(archivo, FileStorageService.Bucket.MESAS, "comprobante-" + codigo);
        jdbc.update("""
                UPDATE mesa_reserva SET estado = 'EN_REVISION', comprobante_archivo = ?, comprobante_subido_en = now(),
                       bloqueo_expira = NULL
                 WHERE id = ? AND estado = 'BLOQUEADA'
                """, ruta, reservaId);
        publicarDespues((Long) r.get("idEvento"), idsDeReserva(r));
        return Resultado.ok("Comprobante recibido", Map.of());
    }

    @Transactional
    public Resultado cancelarPorCliente(String codigo) {
        Map<String, Object> r = reservaInterna(codigo);
        if (r == null) return Resultado.error("La reserva no existe");
        if (!"BLOQUEADA".equals(r.get("estado"))) return Resultado.error("Esta reserva ya no se puede cancelar desde aquí");
        int n = jdbc.update("UPDATE mesa SET estado = 'L', id_reserva = NULL, bloqueo_expira = NULL, version = version + 1 "
                + "WHERE estado = 'T' AND id_reserva = ?", r.get("id"));
        if (n != idsDeReserva(r).size()) throw new Rechazo("Esta reserva ya no se puede cancelar");
        jdbc.update("UPDATE mesa_reserva SET estado = 'CANCELADA', bloqueo_expira = NULL, motivo = 'Cancelada por el cliente' "
                + "WHERE id = ? AND estado = 'BLOQUEADA'", r.get("id"));
        publicarDespues((Long) r.get("idEvento"), idsDeReserva(r));
        return Resultado.ok("Reserva cancelada", Map.of());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> consultarReserva(String codigo) {
        List<Map<String, Object>> filas = jdbc.query(SELECT_RESERVA + " WHERE r.codigo = ?", (rs, i) -> {
            Map<String, Object> m = reserva(rs, false);
            m.put("evento", evento(rs.getLong("id_evento"), false));
            if ("BLOQUEADA".equals(m.get("estado")) && (Long) m.get("segundosRestantes") == 0) {
                m.put("estado", "VENCIDA");
                m.put("motivo", "Venció el tiempo para adjuntar el comprobante");
            }
            if ("CONFIRMADA".equals(m.get("estado"))) m.put("pases", pases.deReserva(rs.getLong("id")));
            return m;
        }, codigo);
        return filas.isEmpty() ? null : filas.get(0);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> reservas(Long eventoId, String estado) {
        String filtro = estado == null || estado.isBlank() ? "" : " AND r.estado = ?";
        Object[] p = filtro.isEmpty() ? new Object[] { eventoId } : new Object[] { eventoId, estado.trim().toUpperCase() };
        return jdbc.query(SELECT_RESERVA + " WHERE r.id_evento = ?" + filtro
                + " ORDER BY CASE r.estado WHEN 'EN_REVISION' THEN 0 WHEN 'BLOQUEADA' THEN 1 ELSE 2 END, r.creado_en DESC",
                (rs, i) -> reserva(rs, true), p);
    }

    @Transactional
    public Resultado confirmar(Long reservaId, Long usuarioId) {
        return confirmar(reservaId, null, null, usuarioId);
    }

    @Transactional
    public Resultado confirmar(Long reservaId, String tipoPase, Integer personas, Long usuarioId) {
        Map<String, Object> r = reservaPorId(reservaId);
        if (r == null) return Resultado.error("La reserva no existe");
        Map<String, Object> datos = jdbc.queryForMap("""
                SELECT COALESCE(r.personas, m.capacidad) AS personas,
                       m.capacidad + COALESCE(a.capacidad, 0) AS capacidad, m.zona
                  FROM mesa_reserva r JOIN mesa m ON m.id = r.id_mesa
                  LEFT JOIN mesa a ON a.id = r.id_mesa_adicional WHERE r.id = ?
                """, reservaId);
        int capacidad = ((Number) datos.get("capacidad")).intValue();
        boolean retiro = Boolean.TRUE.equals(r.get("retiroFisico"));
        int cuantos = retiro || personas == null ? ((Number) datos.get("personas")).intValue() : personas;
        if (!retiro && (cuantos < 1 || cuantos > capacidad)) return Resultado.error("La mesa admite de 1 a " + capacidad + " personas");
        String tipo = limpio(tipoPase, 60);
        if (tipo == null) tipo = datos.get("zona") == null ? "General" : (String) datos.get("zona");
        int n = jdbc.update("UPDATE mesa SET estado = 'O', version = version + 1 WHERE estado = 'R' AND id_reserva = ?",
                reservaId);
        if (n != idsDeReserva(r).size()) throw new Rechazo("Esta reserva no está esperando validación");
        jdbc.update("UPDATE mesa_reserva SET estado = 'CONFIRMADA', validado_por = ?, validado_en = now(), motivo = NULL, "
                + "personas = ?, tipo_pase = ? WHERE id = ? AND estado = 'EN_REVISION'", usuarioId, cuantos, tipo, reservaId);
        if (!retiro) pases.generar(reservaId, (String) r.get("codigo"), cuantos, tipo);
        publicarDespues((Long) r.get("idEvento"), idsDeReserva(r));
        log.info("Reserva de mesa {} confirmada por el usuario {}", reservaId, usuarioId);
        avisos.avisar(reservaId, AvisoReservaMesaService.Tipo.CONFIRMADA);
        if (retiro) return Resultado.ok("Pago validado. El titular ya puede retirar el vale y las manillas", Map.of());
        return Resultado.ok("Pago validado: se generaron " + cuantos + " pase" + (cuantos == 1 ? "" : "s") + " " + tipo, Map.of());
    }

    @Transactional
    public Resultado rechazar(Long reservaId, String motivo, Long usuarioId) {
        String m = limpio(motivo, 300);
        if (m == null) return Resultado.error("Escribe el motivo del rechazo: el cliente lo verá");
        Map<String, Object> r = reservaPorId(reservaId);
        if (r == null) return Resultado.error("La reserva no existe");
        int n = jdbc.update("UPDATE mesa SET estado = 'L', id_reserva = NULL, version = version + 1 "
                + "WHERE estado = 'R' AND id_reserva = ?", reservaId);
        if (n != idsDeReserva(r).size()) throw new Rechazo("Esta reserva no está esperando validación");
        jdbc.update("UPDATE mesa_reserva SET estado = 'RECHAZADA', validado_por = ?, validado_en = now(), motivo = ? "
                + "WHERE id = ? AND estado = 'EN_REVISION'", usuarioId, m, reservaId);
        publicarDespues((Long) r.get("idEvento"), idsDeReserva(r));
        avisos.avisar(reservaId, AvisoReservaMesaService.Tipo.RECHAZADA);
        return Resultado.ok("Pago rechazado: la mesa volvió a estar libre", Map.of());
    }

    @Transactional
    public Resultado liberarNoPresentado(Long reservaId, Long usuarioId) {
        Map<String, Object> r = reservaPorId(reservaId);
        if (r == null) return Resultado.error("La reserva no existe");
        Integer puede = jdbc.queryForObject("""
                SELECT count(*) FROM mesa_reserva r JOIN mesa_evento e ON e.id = r.id_evento
                 WHERE r.id = ? AND r.estado = 'CONFIRMADA' AND r.ingreso_en IS NULL
                   AND e.hora_limite IS NOT NULL AND now() > e.hora_limite
                """, Integer.class, reservaId);
        if (puede == null || puede == 0) {
            return Resultado.error("Solo se libera una mesa confirmada, sin ingreso registrado y pasada la hora límite");
        }
        int n = jdbc.update("UPDATE mesa SET estado = 'L', id_reserva = NULL, version = version + 1 "
                + "WHERE estado = 'O' AND id_reserva = ?", reservaId);
        if (n != idsDeReserva(r).size()) throw new Rechazo("Esta reserva ya no se puede liberar");
        jdbc.update("UPDATE mesa_reserva SET estado = 'CANCELADA', motivo = 'No se presentó antes de la hora límite', "
                + "validado_por = ?, validado_en = now() WHERE id = ?", usuarioId, reservaId);
        publicarDespues((Long) r.get("idEvento"), idsDeReserva(r));
        avisos.avisar(reservaId, AvisoReservaMesaService.Tipo.NO_PRESENTADO);
        return Resultado.ok("Mesa liberada por no presentación", Map.of());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> entrega(Long reservaId) {
        List<Map<String, Object>> filas = jdbc.query(SELECT_RESERVA + " WHERE r.id = ?", (rs, i) -> {
            Map<String, Object> r = reserva(rs, true);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reservaId", rs.getLong("id"));
            m.put("codigo", r.get("codigo"));
            m.put("estado", r.get("estado"));
            m.put("eventoId", rs.getLong("id_evento"));
            m.put("evento", rs.getString("evento_nombre"));
            m.put("tiposPase", tipos(rs.getString("evento_tipos")));
            m.put("mesa", r.get("mesa"));
            m.put("retiroFisico", r.get("retiroFisico"));
            m.put("ingresoEn", r.get("ingresoEn"));
            m.put("zona", r.get("zona"));
            m.put("capacidad", r.get("capacidad"));
            m.put("beneficios", r.get("beneficios"));
            m.put("titular", (r.get("nombre") + " " + r.get("apellidos")).trim());
            m.put("ci", r.get("ci"));
            m.put("celular", r.get("celular"));
            m.put("correo", r.get("correo"));
            m.put("monto", r.get("monto"));
            m.put("personas", r.get("personas"));
            m.put("tipoPase", r.get("tipoPase"));
            m.put("ingresados", r.get("ingresados"));
            m.put("conComprobante", r.get("comprobanteUrl") != null);
            m.put("comprobanteUrl", r.get("comprobanteUrl"));
            m.put("validadoPor", r.get("validadoPor"));
            m.put("validadoEn", r.get("validadoEn"));
            m.put("enOficina", rs.getObject("registrado_por") != null);
            m.put("enlacePublico", "/?reserva=" + r.get("codigo"));
            return m;
        }, reservaId);
        if (filas.isEmpty()) return null;
        Map<String, Object> m = filas.get(0);
        Map<String, Object> ev = evento((Long) m.get("eventoId"), false);
        if (ev != null) {
            m.put("local", ev.get("localNombre"));
            m.put("fechaTexto", ev.get("fechaTexto"));
            m.put("horaLimiteTexto", ev.get("horaLimiteTexto"));
        }
        m.put("pases", "CONFIRMADA".equals(m.get("estado")) ? pases.deReserva(reservaId) : List.of());
        return m;
    }

    @Transactional
    public Resultado regenerarPases(Long reservaId, Integer personas, String tipoPase, Long usuarioId) {
        Map<String, Object> reserva = reservaPorId(reservaId);
        boolean retiro = reserva != null && Boolean.TRUE.equals(reserva.get("retiroFisico"));
        List<Map<String, Object>> f = jdbc.queryForList("""
                SELECT r.codigo, r.estado, r.id_evento, r.id_mesa, r.tipo_pase, COALESCE(r.personas, m.capacidad) AS personas,
                       m.capacidad, m.zona,
                       (SELECT count(*) FROM mesa_pase p WHERE p.id_reserva = r.id AND p.ingreso_en IS NOT NULL) AS usados
                  FROM mesa_reserva r JOIN mesa m ON m.id = r.id_mesa WHERE r.id = ?
                """, reservaId);
        if (f.isEmpty()) return Resultado.error("La reserva no existe");
        Map<String, Object> r = f.get(0);
        if (!"CONFIRMADA".equals(r.get("estado"))) return Resultado.error("Solo se generan pases de una reserva confirmada");
        int capacidad = ((Number) r.get("capacidad")).intValue();
        int usados = ((Number) r.get("usados")).intValue();
        int cuantos = personas == null ? ((Number) r.get("personas")).intValue() : personas;
        int minimo = Math.max(1, usados);
        if (retiro) {
            if (cuantos < 1 || cuantos > 99) return Resultado.error("Elige entre 0 y 98 credenciales adicionales");
            pases.generar(reservaId, (String) r.get("codigo"), cuantos, "Credencial");
            publicarDespues((Long) reserva.get("idEvento"), idsDeReserva(reserva));
            return Resultado.ok("Credenciales generadas", Map.of());
        }
        if (cuantos < minimo || cuantos > capacidad) {
            return Resultado.error("La cantidad de pases debe estar entre " + minimo + " y " + capacidad
                    + (usados > 0 ? " (" + usados + " ya ingresaron)" : ""));
        }
        String tipo = limpio(tipoPase, 60);
        if (tipo == null) tipo = (String) r.get("tipo_pase");
        if (tipo == null) tipo = r.get("zona") == null ? "General" : (String) r.get("zona");
        jdbc.update("UPDATE mesa_reserva SET personas = ?, tipo_pase = ? WHERE id = ?", cuantos, tipo, reservaId);
        pases.generar(reservaId, (String) r.get("codigo"), cuantos, tipo);
        jdbc.update("UPDATE mesa_pase SET tipo = ? WHERE id_reserva = ? AND ingreso_en IS NULL", tipo, reservaId);
        log.info("Pases de la reserva {} generados de nuevo por el usuario {}: {} {}", reservaId, usuarioId, cuantos, tipo);
        return Resultado.ok("Se generaron " + cuantos + " pase" + (cuantos == 1 ? "" : "s") + " " + tipo, Map.of());
    }

    @Transactional
    public Resultado venderEnOficina(Long eventoId, Long mesaId, Cliente c, String tipoPase, Long usuarioId) {
        String error = validarCliente(c);
        if (error == null && limpio(c.apellidos(), 80) == null) error = "Escribe tus apellidos";
        if (error != null) return Resultado.error(error);
        if (!existeEvento(eventoId)) return Resultado.error("El evento no existe");
        List<Object[]> mesa = jdbc.query("""
                SELECT precio, codigo, capacidad, zona FROM mesa WHERE id = ? AND id_evento = ? AND "_estado" = 'A'
                """, (rs, i) -> new Object[] { rs.getBigDecimal(1), rs.getString(2), rs.getInt(3), rs.getString(4) },
                mesaId, eventoId);
        if (mesa.isEmpty()) return Resultado.error("La mesa no existe");
        String codigoMesa = (String) mesa.get(0)[1];
        int capacidad = (Integer) mesa.get(0)[2];
        int personas = c.personas() == null ? capacidad : c.personas();
        if (personas < 1 || personas > capacidad) return Resultado.error("Esta mesa admite de 1 a " + capacidad + " personas");
        if (enAtencion(mesaId)) return Resultado.error("Un cliente está comprando la mesa " + codigoMesa + " en este momento. Elige otra.");
        String tipo = limpio(tipoPase, 60);
        if (tipo == null) tipo = mesa.get(0)[3] == null ? "General" : (String) mesa.get(0)[3];
        int n = jdbc.update("""
                UPDATE mesa SET estado = 'O', bloqueo_expira = NULL, version = version + 1,
                       "_modificacion_id_usuario" = ?, "_fecha_modificacion" = now()
                 WHERE id = ? AND id_evento = ? AND estado = 'L' AND "_estado" = 'A'
                """, usuarioId, mesaId, eventoId);
        if (n == 0) return Resultado.error("La mesa " + codigoMesa + " ya no está disponible. Elige otra.");
        String codigo = nuevoCodigo();
        Long reservaId = jdbc.queryForObject("""
                INSERT INTO mesa_reserva (id_evento, id_mesa, codigo, nombre, apellidos, ci, celular, correo, monto,
                                          estado, validado_por, validado_en, registrado_por, personas, tipo_pase)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'CONFIRMADA', ?, now(), ?, ?, ?) RETURNING id
                """, Long.class, eventoId, mesaId, codigo, limpio(c.nombre(), 80), limpio(c.apellidos(), 80),
                c.ci().trim(), soloDigitos(c.celular()), limpio(c.correo(), 120), mesa.get(0)[0],
                usuarioId, usuarioId, personas, tipo);
        jdbc.update("UPDATE mesa SET id_reserva = ? WHERE id = ?", reservaId, mesaId);
        pases.generar(reservaId, codigo, personas, tipo);
        publicarDespues(eventoId, List.of(mesaId));
        log.info("Mesa {} vendida en oficina por el usuario {} (reserva {})", mesaId, usuarioId, reservaId);
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("reservaId", reservaId);
        datos.put("codigo", codigo);
        return Resultado.ok("Mesa " + codigoMesa + " vendida: " + personas + " pase" + (personas == 1 ? "" : "s") + " " + tipo, datos);
    }

    @Transactional
    public Resultado eliminarEvento(Long id, Long usuarioId) {
        if (!existeEvento(id)) return Resultado.error("El evento no existe");
        Integer vivas = jdbc.queryForObject("""
                SELECT count(*) FROM mesa_reserva
                 WHERE id_evento = ?
                   AND (estado IN ('CONFIRMADA', 'EN_REVISION') OR (estado = 'BLOQUEADA' AND bloqueo_expira > now()))
                """, Integer.class, id);
        if (vivas != null && vivas > 0) {
            return Resultado.error("El evento tiene " + vivas + " reserva" + (vivas == 1 ? "" : "s")
                    + " confirmada" + (vivas == 1 ? "" : "s") + " o en curso. Recházalas o espera a que venzan antes de eliminarlo.");
        }
        jdbc.update("""
                UPDATE mesa SET "_estado" = 'X', version = version + 1, "_fecha_modificacion" = now(),
                       "_modificacion_id_usuario" = ?
                 WHERE id_evento = ? AND "_estado" = 'A'
                """, usuarioId, id);
        jdbc.update("""
                UPDATE mesa_evento SET "_estado" = 'X', publicado = FALSE, "_fecha_modificacion" = now(),
                       "_modificacion_id_usuario" = ?
                 WHERE id = ?
                """, usuarioId, id);
        jdbc.update("UPDATE plantilla_credencial SET id_evento = NULL WHERE id_evento = ?", id);
        avisoPublico.cambio("evento", id);
        log.warn("Evento con mesas {} eliminado por el usuario {}", id, usuarioId);
        return Resultado.ok("Evento eliminado", Map.of());
    }

    @Transactional
    public Resultado registrarIngreso(String codigo, Long usuarioId) {
        String limpioCodigo = codigo == null ? "" : codigo.trim().toUpperCase();
        if (MesaPaseService.esCodigoDePase(limpioCodigo)) {
            Map<String, Object> res = pases.registrarIngreso(limpioCodigo, usuarioId);
            Map<String, Object> detalle = res.get("reserva") == null ? null : consultarReserva((String) res.get("reserva"));
            if (detalle != null) {
                detalle.put("pase", res.get("pase"));
                detalle.put("tipo", res.get("tipo"));
                detalle.put("sentido", res.get("sentido"));
                detalle.put("persona", res.get("persona"));
            }
            return new Resultado(Boolean.TRUE.equals(res.get("ok")), (String) res.get("mensaje"), detalle);
        }
        Map<String, Object> r = reservaInterna(limpioCodigo);
        if (r == null) return Resultado.error("Código no válido");
        Map<String, Object> detalle = consultarReserva((String) r.get("codigo"));
        if (!"CONFIRMADA".equals(r.get("estado"))) {
            return new Resultado(false, "La reserva no está confirmada (" + r.get("estado") + ")", detalle);
        }
        List<String> pendientes = jdbc.queryForList(
                "SELECT codigo FROM mesa_pase WHERE id_reserva = ? AND ingreso_en IS NULL ORDER BY numero LIMIT 1", String.class, r.get("id"));
        if (!Boolean.TRUE.equals(r.get("retiroFisico")) && !pendientes.isEmpty()) return registrarIngreso(pendientes.get(0), usuarioId);
        int n = jdbc.update("UPDATE mesa_reserva SET ingreso_en = now(), ingreso_por = ? WHERE id = ? AND ingreso_en IS NULL",
                usuarioId, r.get("id"));
        detalle = consultarReserva((String) r.get("codigo"));
        if (Boolean.TRUE.equals(r.get("retiroFisico"))) {
            if (n == 0) return new Resultado(false, "El vale y las manillas ya fueron retirados", detalle);
            publicarDespues((Long) r.get("idEvento"), idsDeReserva(r));
            return Resultado.ok("Retiro del vale y las manillas registrado", detalle);
        }
        if (n == 0) return new Resultado(false, "Esta reserva ya registró su ingreso", detalle);
        return Resultado.ok("Ingreso registrado", detalle);
    }

    @Scheduled(fixedDelayString = "${unifex.reserva.barrido-ms:30000}")
    @Transactional
    public void liberarVencidas() {
        List<Object[]> liberadas = jdbc.query("""
                WITH vencidas AS (
                    SELECT id FROM mesa_reserva WHERE estado = 'BLOQUEADA' AND bloqueo_expira <= clock_timestamp()
                    FOR UPDATE SKIP LOCKED
                ), reservas AS (
                    UPDATE mesa_reserva r SET estado = 'VENCIDA', motivo = 'No se adjuntó el comprobante a tiempo'
                     FROM vencidas v WHERE r.id = v.id RETURNING r.id
                )
                UPDATE mesa m SET estado = 'L', id_reserva = NULL, bloqueo_expira = NULL, version = m.version + 1
                  FROM reservas r WHERE m.id_reserva = r.id AND m.estado = 'T'
                RETURNING m.id, m.id_evento
                """, (rs, i) -> new Object[] { rs.getLong(1), rs.getLong(2) });
        for (Object[] f : liberadas) publicarDespues((Long) f[1], List.of((Long) f[0]));
        long vence = System.currentTimeMillis();
        for (Map.Entry<Long, Atencion> e : atenciones.entrySet()) {
            if (!e.getValue().vigente(vence) && atenciones.remove(e.getKey(), e.getValue())) {
                publicarDespues(e.getValue().eventoId(), List.of(e.getKey()));
            }
        }
        if (!liberadas.isEmpty()) log.info("Mesas liberadas por vencimiento: {}", liberadas.size());
        long ahora = System.currentTimeMillis();
        intentos.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long ultimo = e.getValue().peekLast();
                return ultimo == null || ahora - ultimo > VENTANA_INTENTOS_MS;
            }
        });
    }

    public String politicasPorDefecto(int minutos, LocalDateTime horaLimite) {
        String limite = horaLimite == null ? "la hora que indique el local" : "las " + HORA.format(horaLimite);
        return String.join("\n",
                "Tu mesa queda bloqueada " + minutos + " minutos para que pagues y adjuntes el comprobante. Si no lo adjuntas a tiempo, la mesa se libera.",
                "La reserva se confirma solo cuando el local valida tu pago. El QR para retirar el vale y las manillas aparecerá en la página de tu reserva.",
                "Revisa tus datos antes de reservar: no podrán modificarse después. El titular debe presentar su carnet al retirar el vale y las manillas.",
                "La mesa se guarda hasta " + limite + ". Si el titular no llega a esa hora, el local puede liberarla y venderla otra vez sin devolución.",
                "No se admiten más personas que la capacidad indicada en la mesa.",
                "Si el pago no coincide con el monto o el comprobante no se puede leer, la reserva se rechaza, la mesa se libera y te mostramos el motivo.",
                "Puedes cancelar mientras tu mesa esté bloqueada. Después de pagar, cualquier devolución se acuerda directamente con el local.",
                "Los beneficios y el consumo incluidos son los descritos en la mesa y no se cambian por dinero.");
    }

    private static final String SELECT_RESERVA = """
            SELECT r.*, concat_ws(', ', m.codigo, a.codigo) AS mesa_codigo, m.zona AS mesa_zona,
                   m.capacidad + COALESCE(a.capacidad, 0) AS mesa_capacidad,
                   NULLIF(concat_ws(' · ', m.beneficios, a.beneficios), '') AS mesa_beneficios, u.username AS validado_usuario,
                   (SELECT e2.nombre FROM mesa_evento e2 WHERE e2.id = r.id_evento) AS evento_nombre,
                   (SELECT e2.tipos_pase FROM mesa_evento e2 WHERE e2.id = r.id_evento) AS evento_tipos,
                   (SELECT count(*) FROM mesa_pase p WHERE p.id_reserva = r.id AND p.ingreso_en IS NOT NULL) AS ingresados
              FROM mesa_reserva r
              JOIN mesa m ON m.id = r.id_mesa
              LEFT JOIN mesa a ON a.id = r.id_mesa_adicional
              LEFT JOIN usuario u ON u.id = r.validado_por
            """;

    private Map<String, Object> reserva(ResultSet rs, boolean completo) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codigo", rs.getString("codigo"));
        m.put("estado", rs.getString("estado"));
        m.put("nombre", rs.getString("nombre"));
        m.put("apellidos", rs.getString("apellidos"));
        m.put("monto", rs.getBigDecimal("monto"));
        m.put("mesa", rs.getString("mesa_codigo"));
        m.put("mesaIds", rs.getObject("id_mesa_adicional") == null ? List.of(rs.getLong("id_mesa"))
                : List.of(rs.getLong("id_mesa"), rs.getLong("id_mesa_adicional")));
        m.put("retiroFisico", rs.getBoolean("retiro_fisico"));
        m.put("zona", rs.getString("mesa_zona"));
        m.put("capacidad", rs.getInt("mesa_capacidad"));
        m.put("beneficios", rs.getString("mesa_beneficios"));
        m.put("personas", rs.getObject("personas") == null ? rs.getInt("mesa_capacidad") : rs.getInt("personas"));
        m.put("tipoPase", rs.getString("tipo_pase"));
        m.put("ingresados", rs.getLong("ingresados"));
        m.put("bloqueoExpira", fecha(rs.getTimestamp("bloqueo_expira")));
        m.put("segundosRestantes", restantes(rs.getTimestamp("bloqueo_expira")));
        m.put("motivo", rs.getString("motivo"));
        m.put("ingresoEn", fecha(rs.getTimestamp("ingreso_en")));
        m.put("creadoEn", fecha(rs.getTimestamp("creado_en")));
        if (completo) {
            m.put("id", rs.getLong("id"));
            m.put("ci", rs.getString("ci"));
            m.put("celular", rs.getString("celular"));
            m.put("correo", rs.getString("correo"));
            String comp = rs.getString("comprobante_archivo");
            m.put("comprobanteUrl", comp == null ? null : "/files/" + comp);
            m.put("comprobanteSubidoEn", fecha(rs.getTimestamp("comprobante_subido_en")));
            m.put("validadoPor", rs.getString("validado_usuario"));
            m.put("validadoEn", fecha(rs.getTimestamp("validado_en")));
        } else {
            m.put("ci", enmascarar(rs.getString("ci")));
            m.put("celular", enmascarar(rs.getString("celular")));
        }
        return m;
    }

    private Map<String, Object> evento(Long id, boolean completo) {
        List<Map<String, Object>> f = jdbc.query("SELECT * FROM mesa_evento WHERE id = ?", (rs, i) -> evento(rs, completo), id);
        return f.isEmpty() ? null : f.get(0);
    }

    private Map<String, Object> evento(ResultSet rs, boolean completo) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        Timestamp inicio = rs.getTimestamp("fecha_inicio");
        Timestamp limite = rs.getTimestamp("hora_limite");
        int minutos = rs.getInt("minutos_bloqueo");
        m.put("id", rs.getLong("id"));
        m.put("nombre", rs.getString("nombre"));
        m.put("localNombre", rs.getString("local_nombre"));
        m.put("localDireccion", rs.getString("local_direccion"));
        m.put("descripcion", rs.getString("descripcion"));
        m.put("fechaInicio", fecha(inicio));
        m.put("fechaTexto", inicio == null ? null : FECHA.format(inicio.toLocalDateTime()));
        m.put("horaLimite", fecha(limite));
        m.put("horaLimiteTexto", limite == null ? null : HORA.format(limite.toLocalDateTime()));
        m.put("minutosBloqueo", minutos);
        String plano = rs.getString("plano_archivo");
        m.put("planoUrl", plano == null ? null : "/files/" + plano);
        m.put("planoAncho", rs.getObject("plano_ancho"));
        m.put("planoAlto", rs.getObject("plano_alto"));
        String qr = rs.getString("pago_qr_archivo");
        m.put("pagoQrUrl", qr == null ? null : "/files/" + qr);
        String logo = rs.getString("logo_archivo");
        m.put("logoUrl", logo == null ? null : "/files/" + logo);
        m.put("pagoBanco", rs.getString("pago_banco"));
        m.put("pagoCuenta", rs.getString("pago_cuenta"));
        m.put("pagoTitular", rs.getString("pago_titular"));
        m.put("pagoInstrucciones", rs.getString("pago_instrucciones"));
        m.put("celularContacto", rs.getString("celular_contacto"));
        String politicas = rs.getString("politicas");
        m.put("politicasPropias", politicas);
        m.put("politicas", (politicas == null || politicas.isBlank())
                ? politicasPorDefecto(minutos, limite == null ? null : limite.toLocalDateTime()) : politicas);
        m.put("publicado", rs.getBoolean("publicado"));
        m.put("tiposPase", tipos(rs.getString("tipos_pase")));
        m.put("elementos", elementos(rs.getString("plano_elementos")));
        if (!completo) m.remove("politicasPropias");
        return m;
    }

    private List<Map<String, Object>> mesas(Long eventoId, boolean completo) {
        return jdbc.query("""
                SELECT m.*, r.nombre AS r_nombre, r.apellidos AS r_apellidos, r.codigo AS r_codigo
                  FROM mesa m LEFT JOIN mesa_reserva r ON r.id = m.id_reserva
                 WHERE m.id_evento = ? AND m."_estado" = 'A'
                 ORDER BY length(m.codigo), m.codigo
                """, (rs, i) -> mesa(rs, completo), eventoId);
    }

    private Map<String, Object> mesa(ResultSet rs, boolean completo) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        String estado = rs.getString("estado");
        m.put("id", rs.getLong("id"));
        m.put("codigo", rs.getString("codigo"));
        m.put("zona", rs.getString("zona"));
        m.put("capacidad", rs.getInt("capacidad"));
        m.put("precio", rs.getBigDecimal("precio"));
        m.put("beneficios", rs.getString("beneficios"));
        m.put("forma", rs.getString("forma"));
        m.put("color", rs.getString("color"));
        m.put("mapaX", rs.getObject("mapa_x"));
        m.put("mapaY", rs.getObject("mapa_y"));
        m.put("tamano", rs.getDouble("tamano"));
        m.put("estado", completo ? estado : ("T".equals(estado) || "R".equals(estado) ? "R" : estado));
        m.put("enProceso", "L".equals(estado) && enAtencion(rs.getLong("id")));
        if (completo) {
            String nombre = rs.getString("r_nombre");
            m.put("reservadoPor", nombre == null ? null : nombre + " " + rs.getString("r_apellidos"));
            m.put("reservaCodigo", rs.getString("r_codigo"));
            m.put("reservaId", rs.getObject("id_reserva", Long.class));
            m.put("bloqueoExpira", fecha(rs.getTimestamp("bloqueo_expira")));
        }
        return m;
    }

    private void publicarDespues(Long eventoId, List<Long> mesaIds) {
        if (eventoId == null || mesaIds.isEmpty()) return;
        Runnable enviar = () -> {
            try {
                List<Map<String, Object>> estados = jdbc.query("""
                        SELECT m.*, NULL AS r_nombre, NULL AS r_apellidos, NULL AS r_codigo FROM mesa m WHERE m.id = ANY(?)
                        """, (rs, i) -> {
                    Map<String, Object> d = mesa(rs, false);
                    d.put("activa", "A".equals(rs.getString("_estado")));
                    return d;
                }, (Object) mesaIds.toArray(new Long[0]));
                mensajeria.convertAndSend(TOPIC + eventoId, estados);
                mensajeria.convertAndSend(AvisoPublicoService.TOPIC, Map.of("tipo", "mesas", "id", eventoId));
            } catch (RuntimeException e) {
                log.warn("No se pudo difundir el cambio de mesas del evento {}: {}", eventoId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar.run();
                }
            });
        } else {
            enviar.run();
        }
    }

    private Map<String, Object> reservaInterna(String codigo) {
        List<Map<String, Object>> f = jdbc.query("SELECT id, id_mesa, id_mesa_adicional, id_evento, estado, codigo, retiro_fisico FROM mesa_reserva WHERE codigo = ? FOR UPDATE",
                (rs, i) -> filaReserva(rs), codigo);
        return f.isEmpty() ? null : f.get(0);
    }

    private Map<String, Object> reservaPorId(Long id) {
        List<Map<String, Object>> f = jdbc.query("SELECT id, id_mesa, id_mesa_adicional, id_evento, estado, codigo, retiro_fisico FROM mesa_reserva WHERE id = ? FOR UPDATE",
                (rs, i) -> filaReserva(rs), id);
        return f.isEmpty() ? null : f.get(0);
    }

    private static Map<String, Object> filaReserva(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("idMesa", rs.getLong("id_mesa"));
        m.put("idMesaAdicional", rs.getObject("id_mesa_adicional", Long.class));
        m.put("retiroFisico", rs.getBoolean("retiro_fisico"));
        m.put("idEvento", rs.getLong("id_evento"));
        m.put("estado", rs.getString("estado"));
        m.put("codigo", rs.getString("codigo"));
        return m;
    }

    private static List<Long> idsDeReserva(Map<String, Object> r) {
        Long principal = (Long) r.get("idMesa");
        Long adicional = (Long) r.get("idMesaAdicional");
        return adicional == null ? List.of(principal) : List.of(principal, adicional);
    }

    private String validarEvento(EventoDatos d) {
        if (limpio(d.nombre(), 120) == null) return "El evento necesita un nombre";
        if (limpio(d.localNombre(), 120) == null) return "Indica el nombre del local";
        LocalDateTime inicio = fechaHora(d.fechaInicio());
        if (inicio == null) return "Indica la fecha y hora del evento";
        if (d.horaLimite() != null && !d.horaLimite().isBlank() && fechaHora(d.horaLimite()) == null) return "Hora límite no válida";
        if (d.minutosBloqueo() != null && (d.minutosBloqueo() != 5 && d.minutosBloqueo() != 10)) {
            return "El tiempo de bloqueo debe ser de 5 o 10 minutos";
        }
        return null;
    }

    private Object[] parametrosEvento(EventoDatos d, Long usuarioId) {
        LocalDateTime limite = fechaHora(d.horaLimite());
        return new Object[] { limpio(d.nombre(), 120), limpio(d.localNombre(), 120), limpio(d.localDireccion(), 200),
                limpio(d.descripcion(), 500), Timestamp.valueOf(fechaHora(d.fechaInicio())),
                limite == null ? null : Timestamp.valueOf(limite),
                d.minutosBloqueo() == null ? 10 : d.minutosBloqueo(), limpio(d.pagoBanco(), 80),
                limpio(d.pagoCuenta(), 60), limpio(d.pagoTitular(), 120), limpio(d.pagoInstrucciones(), 500),
                limpio(d.celularContacto(), 30), d.politicas() == null || d.politicas().isBlank() ? null : d.politicas().trim(),
                Boolean.TRUE.equals(d.publicado()), usuarioId };
    }

    private static String validarCliente(Cliente c) {
        if (c == null) return "Faltan tus datos";
        if (limpio(c.nombre(), 80) == null) return "Escribe tu nombre";
        if (c.ci() == null || !c.ci().trim().matches("[0-9A-Za-z\\-]{4,20}")) return "Número de carnet no válido";
        String cel = soloDigitos(c.celular());
        if (cel == null || cel.length() < 7 || cel.length() > 15) return "Número de celular no válido";
        if (c.correo() != null && !c.correo().isBlank() && !c.correo().trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            return "Correo no válido";
        }
        return null;
    }

    private boolean permitirIntento(String ip) {
        String clave = ip == null ? "?" : ip;
        long ahora = System.currentTimeMillis();
        Deque<Long> lista = intentos.computeIfAbsent(clave, k -> new ArrayDeque<>());
        synchronized (lista) {
            while (!lista.isEmpty() && ahora - lista.peekFirst() > VENTANA_INTENTOS_MS) lista.pollFirst();
            if (lista.size() >= intentosPorIp) return false;
            lista.addLast(ahora);
            return true;
        }
    }

    private boolean existeEvento(Long id) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM mesa_evento WHERE id = ? AND \"_estado\" = 'A'", Integer.class, id);
        return n != null && n > 0;
    }

    private Long eventoDeMesa(Long mesaId) {
        List<Long> f = jdbc.queryForList("SELECT id_evento FROM mesa WHERE id = ?", Long.class, mesaId);
        return f.isEmpty() ? null : f.get(0);
    }

    private String nuevoCodigo() {
        StringBuilder sb = new StringBuilder("MSA-");
        for (int i = 0; i < 10; i++) sb.append(ALFABETO[azar.nextInt(ALFABETO.length)]);
        return sb.toString();
    }

    private static String fecha(Timestamp t) {
        return t == null ? null : t.toLocalDateTime().toString();
    }

    private static long restantes(Timestamp t) {
        if (t == null) return 0;
        return Math.max(0, (t.getTime() - System.currentTimeMillis() + 999) / 1000);
    }

    private static LocalDateTime fechaHora(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s.trim().length() == 16 ? s.trim() + ":00" : s.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String enmascarar(String ci) {
        if (ci == null || ci.length() < 3) return ci;
        return "•".repeat(ci.length() - 3) + ci.substring(ci.length() - 3);
    }

    private static String soloDigitos(String s) {
        if (s == null) return null;
        String d = s.replaceAll("[^0-9]", "");
        return d.isEmpty() ? null : d;
    }

    private static String color(String c) {
        return c != null && c.matches("#[0-9a-fA-F]{6}") ? c : null;
    }

    private static Double acotar(Double v) {
        return v == null ? null : Math.max(0, Math.min(1, v));
    }

    private static Object[] concat(Object[]... partes) {
        List<Object> todo = new ArrayList<>();
        for (Object[] p : partes) todo.addAll(java.util.Arrays.asList(p));
        return todo.toArray();
    }

    private static String limpio(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.length() > max ? t.substring(0, max) : t;
    }
}

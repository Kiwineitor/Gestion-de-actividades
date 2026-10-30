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
                              String celularContacto, String politicas, Boolean publicado) {
    }

    public record MesaDatos(Long id, String codigo, String zona, Integer capacidad, BigDecimal precio,
                            String beneficios, String forma, String color, Double mapaX, Double mapaY,
                            Double tamano) {
    }

    public record Cliente(String nombre, String apellidos, String ci, String celular, String correo) {
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
        avisoPublico.cambio("evento", id);
        return Resultado.ok("Evento actualizado", Map.of("id", id));
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
    public Resultado quitarQrPago(Long id) {
        if (!existeEvento(id)) return Resultado.error("El evento no existe");
        jdbc.update("UPDATE mesa_evento SET pago_qr_archivo = NULL WHERE id = ?", id);
        avisoPublico.cambio("evento", id);
        return Resultado.ok("QR de pago quitado", Map.of());
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
        return reservar(eventoId, mesaId, c, ip, null);
    }

    @Transactional
    public Resultado reservar(Long eventoId, Long mesaId, Cliente c, String ip, String token) {
        String error = validarCliente(c);
        if (error != null) return Resultado.error(error);
        if (!permitirIntento(ip)) return Resultado.error("Demasiados intentos seguidos. Espera unos minutos y vuelve a intentar.");
        Atencion atencion = atenciones.get(mesaId);
        if (atencion != null && atencion.vigente(System.currentTimeMillis()) && !atencion.token().equals(token)) {
            return Resultado.error("Otra persona está comprando esta mesa en este momento. Elige otra.");
        }
        Map<String, Object> evento = eventoPublico(eventoId);
        if (evento == null) return Resultado.error("El evento no está disponible");
        Integer activas = jdbc.queryForObject("""
                SELECT count(*) FROM mesa_reserva
                 WHERE id_evento = ? AND (ci = ? OR celular = ?)
                   AND (estado = 'EN_REVISION' OR (estado = 'BLOQUEADA' AND bloqueo_expira > now()))
                """, Integer.class, eventoId, c.ci().trim(), soloDigitos(c.celular()));
        if (activas != null && activas > 0) {
            return Resultado.error("Ya tienes una reserva en curso para este evento. Termínala o espera a que se valide.");
        }
        List<Object[]> mesa = jdbc.query("SELECT precio, codigo FROM mesa WHERE id = ? AND id_evento = ? AND \"_estado\" = 'A'",
                (rs, i) -> new Object[] { rs.getBigDecimal(1), rs.getString(2) }, mesaId, eventoId);
        if (mesa.isEmpty()) return Resultado.error("La mesa no existe");
        int minutos = ((Number) evento.get("minutosBloqueo")).intValue();
        String codigo = nuevoCodigo();
        Long reservaId = jdbc.queryForObject("""
                INSERT INTO mesa_reserva (id_evento, id_mesa, codigo, nombre, apellidos, ci, celular, correo, monto,
                                          estado, bloqueo_expira, ip)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'BLOQUEADA', now() + (? * interval '1 minute'), ?) RETURNING id
                """, Long.class, eventoId, mesaId, codigo, limpio(c.nombre(), 80), limpio(c.apellidos(), 80),
                c.ci().trim(), soloDigitos(c.celular()), limpio(c.correo(), 120), mesa.get(0)[0], minutos, ip);
        int n = jdbc.update("""
                UPDATE mesa SET estado = 'T', id_reserva = ?, bloqueo_expira = now() + (? * interval '1 minute'),
                       version = version + 1
                 WHERE id = ? AND id_evento = ? AND estado = 'L' AND "_estado" = 'A'
                """, reservaId, minutos, mesaId, eventoId);
        if (n == 0) throw new Rechazo("La mesa " + mesa.get(0)[1] + " ya no está disponible. Elige otra.");
        atenciones.remove(mesaId);
        publicarDespues(eventoId, List.of(mesaId));
        log.info("Mesa {} bloqueada por la reserva {} ({} min)", mesaId, reservaId, minutos);
        avisos.avisar(reservaId, AvisoReservaMesaService.Tipo.BLOQUEADA);
        return Resultado.ok("Mesa bloqueada", Map.of("codigo", codigo));
    }

    @Transactional
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
        Long mesaId = (Long) r.get("idMesa");
        int n = jdbc.update("""
                UPDATE mesa SET estado = 'R', bloqueo_expira = NULL, version = version + 1
                 WHERE id = ? AND estado = 'T' AND id_reserva = ? AND bloqueo_expira > now()
                """, mesaId, reservaId);
        if (n == 0) return Resultado.error("El tiempo para adjuntar el comprobante venció y la mesa se liberó");
        String ruta = archivos.save(archivo, FileStorageService.Bucket.MESAS, "comprobante-" + codigo);
        jdbc.update("""
                UPDATE mesa_reserva SET estado = 'EN_REVISION', comprobante_archivo = ?, comprobante_subido_en = now(),
                       bloqueo_expira = NULL
                 WHERE id = ? AND estado = 'BLOQUEADA'
                """, ruta, reservaId);
        publicarDespues((Long) r.get("idEvento"), List.of(mesaId));
        return Resultado.ok("Comprobante recibido", Map.of());
    }

    @Transactional
    public Resultado cancelarPorCliente(String codigo) {
        Map<String, Object> r = reservaInterna(codigo);
        if (r == null) return Resultado.error("La reserva no existe");
        if (!"BLOQUEADA".equals(r.get("estado"))) return Resultado.error("Esta reserva ya no se puede cancelar desde aquí");
        jdbc.update("UPDATE mesa SET estado = 'L', id_reserva = NULL, bloqueo_expira = NULL, version = version + 1 "
                + "WHERE id = ? AND estado = 'T' AND id_reserva = ?", r.get("idMesa"), r.get("id"));
        jdbc.update("UPDATE mesa_reserva SET estado = 'CANCELADA', bloqueo_expira = NULL, motivo = 'Cancelada por el cliente' "
                + "WHERE id = ? AND estado = 'BLOQUEADA'", r.get("id"));
        publicarDespues((Long) r.get("idEvento"), List.of((Long) r.get("idMesa")));
        return Resultado.ok("Reserva cancelada", Map.of());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> consultarReserva(String codigo) {
        List<Map<String, Object>> filas = jdbc.query(SELECT_RESERVA + " WHERE r.codigo = ?", (rs, i) -> {
            Map<String, Object> m = reserva(rs, false);
            m.put("evento", evento(rs.getLong("id_evento"), false));
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
        Map<String, Object> r = reservaPorId(reservaId);
        if (r == null) return Resultado.error("La reserva no existe");
        int n = jdbc.update("UPDATE mesa SET estado = 'O', version = version + 1 WHERE id = ? AND estado = 'R' AND id_reserva = ?",
                r.get("idMesa"), reservaId);
        if (n == 0) return Resultado.error("Esta reserva no está esperando validación");
        jdbc.update("UPDATE mesa_reserva SET estado = 'CONFIRMADA', validado_por = ?, validado_en = now(), motivo = NULL "
                + "WHERE id = ? AND estado = 'EN_REVISION'", usuarioId, reservaId);
        publicarDespues((Long) r.get("idEvento"), List.of((Long) r.get("idMesa")));
        log.info("Reserva de mesa {} confirmada por el usuario {}", reservaId, usuarioId);
        avisos.avisar(reservaId, AvisoReservaMesaService.Tipo.CONFIRMADA);
        return Resultado.ok("Pago validado: la mesa quedó vendida", Map.of());
    }

    @Transactional
    public Resultado rechazar(Long reservaId, String motivo, Long usuarioId) {
        String m = limpio(motivo, 300);
        if (m == null) return Resultado.error("Escribe el motivo del rechazo: el cliente lo verá");
        Map<String, Object> r = reservaPorId(reservaId);
        if (r == null) return Resultado.error("La reserva no existe");
        int n = jdbc.update("UPDATE mesa SET estado = 'L', id_reserva = NULL, version = version + 1 "
                + "WHERE id = ? AND estado = 'R' AND id_reserva = ?", r.get("idMesa"), reservaId);
        if (n == 0) return Resultado.error("Esta reserva no está esperando validación");
        jdbc.update("UPDATE mesa_reserva SET estado = 'RECHAZADA', validado_por = ?, validado_en = now(), motivo = ? "
                + "WHERE id = ? AND estado = 'EN_REVISION'", usuarioId, m, reservaId);
        publicarDespues((Long) r.get("idEvento"), List.of((Long) r.get("idMesa")));
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
        jdbc.update("UPDATE mesa SET estado = 'L', id_reserva = NULL, version = version + 1 "
                + "WHERE id = ? AND estado = 'O' AND id_reserva = ?", r.get("idMesa"), reservaId);
        jdbc.update("UPDATE mesa_reserva SET estado = 'CANCELADA', motivo = 'No se presentó antes de la hora límite', "
                + "validado_por = ?, validado_en = now() WHERE id = ?", usuarioId, reservaId);
        publicarDespues((Long) r.get("idEvento"), List.of((Long) r.get("idMesa")));
        avisos.avisar(reservaId, AvisoReservaMesaService.Tipo.NO_PRESENTADO);
        return Resultado.ok("Mesa liberada por no presentación", Map.of());
    }

    @Transactional
    public Resultado registrarIngreso(String codigo, Long usuarioId) {
        Map<String, Object> r = reservaInterna(codigo == null ? "" : codigo.trim().toUpperCase());
        if (r == null) return Resultado.error("Código no válido");
        Map<String, Object> detalle = consultarReserva((String) r.get("codigo"));
        if (!"CONFIRMADA".equals(r.get("estado"))) {
            return new Resultado(false, "La reserva no está confirmada (" + r.get("estado") + ")", detalle);
        }
        int n = jdbc.update("UPDATE mesa_reserva SET ingreso_en = now(), ingreso_por = ? WHERE id = ? AND ingreso_en IS NULL",
                usuarioId, r.get("id"));
        detalle = consultarReserva((String) r.get("codigo"));
        if (n == 0) return new Resultado(false, "Esta reserva ya registró su ingreso", detalle);
        return Resultado.ok("Ingreso registrado", detalle);
    }

    @Scheduled(fixedDelayString = "${unifex.reserva.barrido-ms:30000}")
    @Transactional
    public void liberarVencidas() {
        List<Object[]> liberadas = jdbc.query("""
                WITH vencidas AS (
                    SELECT id, id_reserva FROM mesa
                     WHERE estado = 'T' AND bloqueo_expira < now()
                     FOR UPDATE SKIP LOCKED)
                UPDATE mesa m SET estado = 'L', id_reserva = NULL, bloqueo_expira = NULL, version = m.version + 1
                  FROM vencidas v
                 WHERE m.id = v.id AND m.estado = 'T'
                RETURNING m.id, m.id_evento, v.id_reserva
                """, (rs, i) -> new Object[] { rs.getLong(1), rs.getLong(2), rs.getObject(3, Long.class) });
        jdbc.update("""
                UPDATE mesa_reserva SET estado = 'VENCIDA', motivo = 'No se adjuntó el comprobante a tiempo'
                 WHERE estado = 'BLOQUEADA' AND bloqueo_expira < now()
                """);
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
                "La reserva se confirma solo cuando el local valida tu pago. Tu código QR de ingreso aparecerá en la página de tu reserva.",
                "Revisa tus datos antes de reservar: no podrán modificarse después. El titular debe presentar su carnet en el ingreso.",
                "La mesa se guarda hasta " + limite + ". Si el titular no llega a esa hora, el local puede liberarla y venderla otra vez sin devolución.",
                "No se admiten más personas que la capacidad indicada en la mesa.",
                "Si el pago no coincide con el monto o el comprobante no se puede leer, la reserva se rechaza, la mesa se libera y te mostramos el motivo.",
                "Puedes cancelar mientras tu mesa esté bloqueada. Después de pagar, cualquier devolución se acuerda directamente con el local.",
                "Los beneficios y el consumo incluidos son los descritos en la mesa y no se cambian por dinero.");
    }

    private static final String SELECT_RESERVA = """
            SELECT r.*, m.codigo AS mesa_codigo, m.zona AS mesa_zona, m.capacidad AS mesa_capacidad,
                   m.beneficios AS mesa_beneficios, u.username AS validado_usuario
              FROM mesa_reserva r
              JOIN mesa m ON m.id = r.id_mesa
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
        m.put("zona", rs.getString("mesa_zona"));
        m.put("capacidad", rs.getInt("mesa_capacidad"));
        m.put("beneficios", rs.getString("mesa_beneficios"));
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
        List<Map<String, Object>> f = jdbc.query("SELECT id, id_mesa, id_evento, estado, codigo FROM mesa_reserva WHERE codigo = ?",
                (rs, i) -> filaReserva(rs), codigo);
        return f.isEmpty() ? null : f.get(0);
    }

    private Map<String, Object> reservaPorId(Long id) {
        List<Map<String, Object>> f = jdbc.query("SELECT id, id_mesa, id_evento, estado, codigo FROM mesa_reserva WHERE id = ?",
                (rs, i) -> filaReserva(rs), id);
        return f.isEmpty() ? null : f.get(0);
    }

    private static Map<String, Object> filaReserva(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("idMesa", rs.getLong("id_mesa"));
        m.put("idEvento", rs.getLong("id_evento"));
        m.put("estado", rs.getString("estado"));
        m.put("codigo", rs.getString("codigo"));
        return m;
    }

    private String validarEvento(EventoDatos d) {
        if (limpio(d.nombre(), 120) == null) return "El evento necesita un nombre";
        if (limpio(d.localNombre(), 120) == null) return "Indica el nombre del local";
        LocalDateTime inicio = fechaHora(d.fechaInicio());
        if (inicio == null) return "Indica la fecha y hora del evento";
        if (d.horaLimite() != null && !d.horaLimite().isBlank() && fechaHora(d.horaLimite()) == null) return "Hora límite no válida";
        if (d.minutosBloqueo() != null && (d.minutosBloqueo() < 2 || d.minutosBloqueo() > 30)) {
            return "El tiempo de bloqueo debe estar entre 2 y 30 minutos";
        }
        return null;
    }

    private Object[] parametrosEvento(EventoDatos d, Long usuarioId) {
        LocalDateTime limite = fechaHora(d.horaLimite());
        return new Object[] { limpio(d.nombre(), 120), limpio(d.localNombre(), 120), limpio(d.localDireccion(), 200),
                limpio(d.descripcion(), 500), Timestamp.valueOf(fechaHora(d.fechaInicio())),
                limite == null ? null : Timestamp.valueOf(limite),
                d.minutosBloqueo() == null ? 5 : d.minutosBloqueo(), limpio(d.pagoBanco(), 80),
                limpio(d.pagoCuenta(), 60), limpio(d.pagoTitular(), 120), limpio(d.pagoInstrucciones(), 500),
                limpio(d.celularContacto(), 30), d.politicas() == null || d.politicas().isBlank() ? null : d.politicas().trim(),
                Boolean.TRUE.equals(d.publicado()), usuarioId };
    }

    private static String validarCliente(Cliente c) {
        if (c == null) return "Faltan tus datos";
        if (limpio(c.nombre(), 80) == null) return "Escribe tu nombre";
        if (limpio(c.apellidos(), 80) == null) return "Escribe tus apellidos";
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
        return Math.max(0, (t.getTime() - System.currentTimeMillis()) / 1000);
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

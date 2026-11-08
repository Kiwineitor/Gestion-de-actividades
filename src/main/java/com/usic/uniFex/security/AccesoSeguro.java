package com.usic.uniFex.security;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.entity.Usuario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccesoSeguro {

    public static final String TERMINOS_VERSION = "2026-11";
    public static final String PENDIENTE_CLAVE = "CLAVE";
    public static final String PENDIENTE_TERMINOS = "TERMINOS";

    private static final int AVISO_DESDE = 5;
    private static final int MAX_FALLOS_IP = 30;
    private static final long VENTANA_MS = 30 * 60_000L;
    private static final long BLOQUEO_IP_MS = 15 * 60_000L;
    private static final String ALFABETO_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final java.security.SecureRandom AZAR = new java.security.SecureRandom();
    private static final long CACHE_MS = 10_000L;
    private static final int CLAVE_MINIMA = 10;
    private static final Set<String> CLAVES_PROHIBIDAS = Set.of("admin1fexpo2026", "admin2fexpo2026", "admin1dev2026",
            "admin2dev2026", "1234567890", "contrasena1", "password123", "qwerty1234", "unifex2026", "administrador1");

    private final IUsuarioDao usuarios;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;

    @Value("${unifex.admin.admin1-password:}")
    private String clave1;

    @Value("${unifex.admin.admin2-password:}")
    private String clave2;

    private record Contador(int fallos, long desde, long bloqueadoHasta) {
    }

    private record Estado(boolean valida, long hasta) {
    }

    private final Map<String, Contador> fallos = new ConcurrentHashMap<>();
    private final Map<String, Estado> sesiones = new ConcurrentHashMap<>();

    public record Ingreso(boolean ok, int estado, String codigo, String mensaje, Usuario usuario) {
        static Ingreso error(int estado, String codigo, String mensaje) {
            return new Ingreso(false, estado, codigo, mensaje, null);
        }
    }

    @Transactional
    public Ingreso ingresar(String usuario, String clave, String ip) {
        String nombre = usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
        long ahora = System.currentTimeMillis();
        String llave = llave(nombre, ip);
        Ingreso pausa = enPausa(llave, ip, ahora);
        if (pausa != null) return pausa;
        Usuario u = nombre.isEmpty() ? null : usuarios.findByUsername(usuario.trim()).orElse(null);
        if (u == null || clave == null || !encoder.matches(clave, u.getPassword())) {
            int n = fallo(llave, ahora);
            falloIp(ip, ahora);
            return new Ingreso(false, 401, u == null ? "NO_EXISTE" : "CLAVE", mensajeFallo(n), u);
        }
        if (!sellado(u)) {
            log.error("Sello invalido para el usuario {}: su contrasena o rol se modificaron fuera del sistema", u.getUsername());
            return new Ingreso(false, 423, "ALTERADO",
                    "Esta cuenta fue modificada fuera del sistema y quedó bloqueada por seguridad. Pide al responsable técnico que restablezca la contraseña.", u);
        }
        fallos.remove(llave);
        String estado = u.getEstado();
        if (estado != null && !"ACTIVO".equalsIgnoreCase(estado.trim())) {
            return new Ingreso(false, 403, "INACTIVO", "Usuario inactivo", u);
        }
        return new Ingreso(true, 200, null, null, u);
    }

    public List<String> pendientes(Usuario u) {
        List<String> p = new ArrayList<>();
        if (!TERMINOS_VERSION.equals(u.getTerminosVersion())) p.add(PENDIENTE_TERMINOS);
        if (u.isDebeCambiarClave()) p.add(PENDIENTE_CLAVE);
        return p;
    }

    public String validarClaveNueva(String nueva, String username) {
        if (nueva == null || nueva.length() < CLAVE_MINIMA) return "La contraseña debe tener al menos " + CLAVE_MINIMA + " caracteres.";
        if (nueva.length() > 72) return "La contraseña es demasiado larga (máximo 72 caracteres).";
        if (!nueva.matches(".*\\p{L}.*") || !nueva.matches(".*\\d.*")) return "Usa letras y números en la contraseña.";
        String baja = nueva.toLowerCase(Locale.ROOT);
        if (username != null && baja.contains(username.trim().toLowerCase(Locale.ROOT))) return "La contraseña no puede contener tu nombre de usuario.";
        if (CLAVES_PROHIBIDAS.contains(baja) || baja.equalsIgnoreCase(clave1) || baja.equalsIgnoreCase(clave2)) {
            return "Esa contraseña es la inicial o es demasiado conocida. Elige otra.";
        }
        return null;
    }

    @Transactional
    public String cambiarClave(Long usuarioId, String actual, String nueva) {
        Usuario u = usuarios.findById(usuarioId).orElse(null);
        if (u == null) return "Usuario no encontrado.";
        if (actual == null || !encoder.matches(actual, u.getPassword())) return "La contraseña actual no es correcta.";
        if (encoder.matches(nueva == null ? "" : nueva, u.getPassword())) return "La nueva contraseña debe ser distinta de la actual.";
        String problema = validarClaveNueva(nueva, u.getUsername());
        if (problema != null) return problema;
        u.setPassword(encoder.encode(nueva));
        u.setDebeCambiarClave(false);
        u.setClaveCambiadaEn(new Date());
        u.cerrarSesiones();
        usuarios.save(u);
        olvidarSesion(u.getId());
        log.info("El usuario {} cambio su contrasena", u.getUsername());
        return null;
    }

    @Transactional
    public void aceptarTerminos(Long usuarioId) {
        usuarios.findById(usuarioId).ifPresent(u -> {
            u.setTerminosVersion(TERMINOS_VERSION);
            u.setTerminosAceptadosEn(new Date());
            usuarios.save(u);
        });
    }

    public boolean sesionValida(Long usuarioId, Integer version) {
        if (usuarioId == null) return false;
        String clave = usuarioId + ":" + (version == null ? 0 : version);
        long ahora = System.currentTimeMillis();
        Estado e = sesiones.get(clave);
        if (e != null && e.hasta() > ahora) return e.valida();
        boolean valida = consultarSesion(usuarioId, version == null ? 0 : version);
        sesiones.put(clave, new Estado(valida, ahora + CACHE_MS));
        return valida;
    }

    public void olvidarSesion(Long usuarioId) {
        if (usuarioId == null) return;
        String prefijo = usuarioId + ":";
        sesiones.keySet().removeIf(k -> k.startsWith(prefijo));
    }

    private boolean consultarSesion(Long usuarioId, int version) {
        List<Map<String, Object>> f = jdbc.queryForList("""
                SELECT username, password, rol_id, "_estado" AS estado, sesion_version, sello FROM usuario WHERE id = ?
                """, usuarioId);
        if (f.isEmpty()) return false;
        Map<String, Object> u = f.getFirst();
        Object estado = u.get("estado");
        if (estado != null && !"ACTIVO".equalsIgnoreCase(estado.toString().trim())) return false;
        if (((Number) u.get("sesion_version")).intValue() != version) return false;
        Long rol = u.get("rol_id") == null ? null : ((Number) u.get("rol_id")).longValue();
        return SelloUsuario.coincide((String) u.get("sello"), (String) u.get("username"), (String) u.get("password"), rol);
    }

    private boolean sellado(Usuario u) {
        return SelloUsuario.coincide(u.getSello(), u.getUsername(), u.getPassword(), u.getRol() == null ? null : u.getRol().getId());
    }

    @Transactional
    public int sellarFaltantes(boolean todos) {
        int n = 0;
        for (Usuario u : usuarios.findAll()) {
            if (todos || u.getSello() == null || u.getSello().isBlank()) {
                u.resellar();
                usuarios.save(u);
                n++;
            }
        }
        sesiones.clear();
        return n;
    }

    @Transactional
    public boolean restablecer(String username, String claveTemporal) {
        Usuario u = usuarios.findByUsername(username.trim()).orElse(null);
        if (u == null) return false;
        u.setPassword(encoder.encode(claveTemporal));
        u.setDebeCambiarClave(true);
        u.setEstado("ACTIVO");
        u.cerrarSesiones();
        usuarios.save(u);
        sesiones.clear();
        desbloquear(username);
        return true;
    }

    public void desbloquear(String username) {
        if (username == null) return;
        String prefijo = "u:" + username.trim().toLowerCase(Locale.ROOT) + "|";
        fallos.keySet().removeIf(k -> k.startsWith(prefijo));
    }

    public java.util.Set<String> enPausa() {
        long ahora = System.currentTimeMillis();
        java.util.Set<String> r = new java.util.TreeSet<>();
        fallos.forEach((k, c) -> {
            if (k.startsWith("u:") && c.bloqueadoHasta() > ahora) r.add(k.substring(2, k.indexOf('|')));
        });
        return r;
    }

    public boolean tieneCodigo(Usuario u) {
        return u.getCodigoRecuperacion() != null && !u.getCodigoRecuperacion().isBlank();
    }

    @Transactional
    public String generarCodigo(Long usuarioId) {
        Usuario u = usuarios.findById(usuarioId).orElse(null);
        if (u == null) return null;
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i > 0 && i % 4 == 0) s.append('-');
            s.append(ALFABETO_CODIGO.charAt(AZAR.nextInt(ALFABETO_CODIGO.length())));
        }
        String codigo = s.toString();
        u.setCodigoRecuperacion(encoder.encode(normalizarCodigo(codigo)));
        u.setCodigoRecuperacionEn(new Date());
        usuarios.save(u);
        return codigo;
    }

    public record Recuperacion(boolean ok, int estado, String mensaje, String nuevoCodigo, Usuario usuario) {
    }

    @Transactional
    public Recuperacion recuperar(String usuario, String codigo, String nueva, String ip) {
        String nombre = usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
        long ahora = System.currentTimeMillis();
        String llave = llave(nombre, ip);
        Ingreso pausa = enPausa(llave, ip, ahora);
        if (pausa != null) return new Recuperacion(false, 429, pausa.mensaje(), null, null);
        Usuario u = nombre.isEmpty() ? null : usuarios.findByUsername(usuario.trim()).orElse(null);
        boolean valido = u != null && tieneCodigo(u) && codigo != null
                && encoder.matches(normalizarCodigo(codigo), u.getCodigoRecuperacion());
        if (!valido) {
            int n = fallo(llave, ahora);
            falloIp(ip, ahora);
            return new Recuperacion(false, 401, "El usuario o el código de recuperación no son correctos."
                    + (n >= AVISO_DESDE - 2 && n < AVISO_DESDE ? " Te queda" + (AVISO_DESDE - n == 1 ? " 1 intento" : "n " + (AVISO_DESDE - n) + " intentos") + " antes de una pausa." : ""), null, u);
        }
        String estado = u.getEstado();
        if (estado != null && !"ACTIVO".equalsIgnoreCase(estado.trim())) {
            return new Recuperacion(false, 403, "Este usuario está inactivo. Pide a un administrador que lo active.", null, u);
        }
        String problema = validarClaveNueva(nueva, u.getUsername());
        if (problema != null) return new Recuperacion(false, 422, problema, null, u);
        u.setPassword(encoder.encode(nueva));
        u.setDebeCambiarClave(false);
        u.setClaveCambiadaEn(new Date());
        u.cerrarSesiones();
        usuarios.save(u);
        olvidarSesion(u.getId());
        fallos.remove(llave);
        String nuevo = generarCodigo(u.getId());
        log.info("El usuario {} recupero su acceso con su codigo de recuperacion", u.getUsername());
        return new Recuperacion(true, 200, "Listo: tu contraseña se cambió y se cerraron las sesiones abiertas.", nuevo, u);
    }

    private static String normalizarCodigo(String codigo) {
        return codigo == null ? "" : codigo.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private static String llave(String nombre, String ip) {
        return "u:" + nombre + "|" + ip;
    }

    private Ingreso enPausa(String llave, String ip, long ahora) {
        long espera = Math.max(restante(llave, ahora), restante("ip:" + ip, ahora));
        if (espera <= 0) return null;
        long segundos = Math.max(1, (espera + 999) / 1000);
        String tiempo = segundos < 90 ? segundos + " segundos" : ((segundos + 59) / 60) + " minutos";
        return Ingreso.error(429, "BLOQUEADO", "Por seguridad, espera " + tiempo + " antes de volver a intentar. "
                + "Si olvidaste tu contraseña usa «¿Olvidaste tu contraseña?» con tu código de recuperación o pide a un administrador que te la restablezca.");
    }

    private static String mensajeFallo(int n) {
        if (n >= AVISO_DESDE) return "Usuario o contraseña incorrectos. Espera un momento antes de volver a intentar.";
        int quedan = AVISO_DESDE - n;
        if (quedan <= 2) return "Usuario o contraseña incorrectos. Te queda" + (quedan == 1 ? " 1 intento" : "n " + quedan + " intentos")
                + " antes de una pausa corta. ¿La olvidaste? Usa «¿Olvidaste tu contraseña?».";
        return "Usuario o contraseña incorrectos";
    }

    private static long pausaPara(int fallos) {
        if (fallos < AVISO_DESDE) return 0;
        if (fallos < 8) return 60_000L;
        if (fallos < 10) return 5 * 60_000L;
        return 15 * 60_000L;
    }

    private long restante(String clave, long ahora) {
        Contador c = fallos.get(clave);
        return c == null ? 0 : Math.max(0, c.bloqueadoHasta() - ahora);
    }

    private int fallo(String clave, long ahora) {
        Contador c = fallos.compute(clave, (k, previo) -> {
            int n = previo == null || ahora - previo.desde() > VENTANA_MS ? 1 : previo.fallos() + 1;
            long desde = previo == null || ahora - previo.desde() > VENTANA_MS ? ahora : previo.desde();
            long pausa = pausaPara(n);
            return new Contador(n, desde, pausa > 0 ? ahora + pausa : 0);
        });
        limpiarViejos(ahora);
        return c.fallos();
    }

    private void falloIp(String ip, long ahora) {
        fallos.compute("ip:" + ip, (k, c) -> {
            if (c == null || ahora - c.desde() > VENTANA_MS) return new Contador(1, ahora, 0);
            int n = c.fallos() + 1;
            return new Contador(n, c.desde(), n >= MAX_FALLOS_IP ? ahora + BLOQUEO_IP_MS : c.bloqueadoHasta());
        });
    }

    private void limpiarViejos(long ahora) {
        if (fallos.size() > 5000) fallos.entrySet().removeIf(e -> ahora - e.getValue().desde() > VENTANA_MS && e.getValue().bloqueadoHasta() < ahora);
    }
}

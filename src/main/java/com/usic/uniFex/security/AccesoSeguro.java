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

    private static final int MAX_FALLOS_USUARIO = 5;
    private static final int MAX_FALLOS_IP = 20;
    private static final long VENTANA_MS = 15 * 60_000L;
    private static final long BLOQUEO_MS = 15 * 60_000L;
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
        long espera = Math.max(restante("u:" + nombre, ahora), restante("ip:" + ip, ahora));
        if (espera > 0) {
            long minutos = Math.max(1, (espera + 59_999) / 60_000);
            return Ingreso.error(429, "BLOQUEADO", "Demasiados intentos fallidos. Por seguridad espera " + minutos
                    + " minuto" + (minutos == 1 ? "" : "s") + " antes de volver a intentar.");
        }
        Usuario u = nombre.isEmpty() ? null : usuarios.findByUsername(usuario.trim()).orElse(null);
        if (u == null || clave == null || !encoder.matches(clave, u.getPassword())) {
            fallo("u:" + nombre, MAX_FALLOS_USUARIO, ahora);
            fallo("ip:" + ip, MAX_FALLOS_IP, ahora);
            return new Ingreso(false, 401, u == null ? "NO_EXISTE" : "CLAVE", "Usuario o contraseña incorrectos", u);
        }
        if (!sellado(u)) {
            log.error("Sello invalido para el usuario {}: su contrasena o rol se modificaron fuera del sistema", u.getUsername());
            return new Ingreso(false, 423, "ALTERADO",
                    "Esta cuenta fue modificada fuera del sistema y quedó bloqueada por seguridad. Pide al responsable técnico que restablezca la contraseña.", u);
        }
        fallos.remove("u:" + nombre);
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
        fallos.remove("u:" + username.trim().toLowerCase(Locale.ROOT));
        return true;
    }

    private long restante(String clave, long ahora) {
        Contador c = fallos.get(clave);
        return c == null ? 0 : Math.max(0, c.bloqueadoHasta() - ahora);
    }

    private void fallo(String clave, int maximo, long ahora) {
        fallos.compute(clave, (k, c) -> {
            if (c == null || ahora - c.desde() > VENTANA_MS) return new Contador(1, ahora, 0);
            int n = c.fallos() + 1;
            return new Contador(n, c.desde(), n >= maximo ? ahora + BLOQUEO_MS : c.bloqueadoHasta());
        });
        if (fallos.size() > 5000) fallos.entrySet().removeIf(e -> ahora - e.getValue().desde() > VENTANA_MS && e.getValue().bloqueadoHasta() < ahora);
    }
}

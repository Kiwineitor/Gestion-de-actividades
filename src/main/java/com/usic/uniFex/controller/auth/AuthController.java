package com.usic.uniFex.controller.auth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.IService.IUsuarioService;
import com.usic.uniFex.model.entity.Usuario;
import com.usic.uniFex.model.service.GestionPersonalApoyoService;
import com.usic.uniFex.model.service.HistorialService;
import com.usic.uniFex.model.service.MantenimientoService;
import com.usic.uniFex.security.AccesoSeguro;
import com.usic.uniFex.security.JwtService;
import com.usic.uniFex.security.JwtUser;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final IUsuarioService usuarioService;
    private final JwtService jwtService;
    private final MantenimientoService mantenimientoService;
    private final GestionPersonalApoyoService personalApoyo;
    private final HistorialService historial;
    private final AccesoSeguro acceso;

    public record LoginRequest(String usuario, String contrasena) {
    }

    public record CambioClave(String actual, String nueva) {
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest req, HttpServletRequest http) {
        String ip = ip(http);
        AccesoSeguro.Ingreso r = acceso.ingresar(req.usuario(), req.contrasena(), ip);
        if (!r.ok()) {
            String intento = req.usuario() == null ? "" : req.usuario().trim();
            if (intento.length() > 60) intento = intento.substring(0, 60);
            Usuario u = r.usuario();
            String texto = switch (String.valueOf(r.codigo())) {
                case "NO_EXISTE" -> "Intentó entrar con un usuario que no existe";
                case "CLAVE" -> "Escribió una contraseña incorrecta al iniciar sesión";
                case "BLOQUEADO" -> "Intento de ingreso bloqueado por demasiados fallos";
                case "ALTERADO" -> "Ingreso rechazado: la cuenta fue modificada fuera del sistema";
                default -> "Ingreso rechazado: usuario inactivo";
            };
            historial.registrar(new HistorialService.Entrada(u == null ? null : u.getId(), intento, null, "Sesiones",
                    texto, "POST", "/api/auth/login", null, r.estado(), false, ip, null));
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("ok", false);
            cuerpo.put("codigo", r.codigo());
            cuerpo.put("mensaje", r.mensaje());
            return ResponseEntity.status(r.estado()).body(cuerpo);
        }
        Usuario u = r.usuario();
        String rol = (u.getRol() != null && u.getRol().getNombre() != null) ? u.getRol().getNombre() : "";
        var mantenimiento = mantenimientoService.estado();
        if (mantenimiento.activo() && !mantenimientoService.permiteRol(rol)) {
            return ResponseEntity.status(423).body(Map.<String, Object>of(
                    "ok", false,
                    "codigo", "MANTENIMIENTO",
                    "mensaje", mantenimiento.mensaje()));
        }
        try {
            personalApoyo.asegurarFichaAlIngresar(u.getId());
        } catch (Exception e) {
            log.warn("No se pudo asegurar la ficha de apoyo al ingresar (usuario {})", u.getId(), e);
        }
        historial.registrar(new HistorialService.Entrada(u.getId(), u.getUsername(), rol, "Sesiones", "Inició sesión",
                "POST", "/api/auth/login", null, 200, true, ip, null));
        return ResponseEntity.ok(respuesta(u));
    }

    @GetMapping("/yo")
    public ResponseEntity<Map<String, Object>> yo() {
        Usuario u = actual();
        if (u == null) return noAutenticado();
        Map<String, Object> m = respuesta(u);
        m.remove("token");
        m.put("terminosVersion", AccesoSeguro.TERMINOS_VERSION);
        return ResponseEntity.ok(m);
    }

    @PostMapping("/aceptar-terminos")
    public ResponseEntity<Map<String, Object>> aceptarTerminos() {
        Usuario u = actual();
        if (u == null) return noAutenticado();
        acceso.aceptarTerminos(u.getId());
        historial.registrar(new HistorialService.Entrada(u.getId(), u.getUsername(), null, "Sesiones",
                "Aceptó los términos de uso (versión " + AccesoSeguro.TERMINOS_VERSION + ")", "POST", "/api/auth/aceptar-terminos", null, 200, true, null, null));
        return ResponseEntity.ok(respuesta(usuarioService.findById(u.getId())));
    }

    @PostMapping("/cambiar-clave")
    public ResponseEntity<Map<String, Object>> cambiarClave(@RequestBody CambioClave req) {
        Usuario u = actual();
        if (u == null) return noAutenticado();
        String error = acceso.cambiarClave(u.getId(), req == null ? null : req.actual(), req == null ? null : req.nueva());
        if (error != null) return ResponseEntity.unprocessableEntity().body(Map.of("ok", false, "mensaje", error));
        historial.registrar(new HistorialService.Entrada(u.getId(), u.getUsername(), null, "Sesiones",
                "Cambió su contraseña", "POST", "/api/auth/cambiar-clave", null, 200, true, null, null));
        Map<String, Object> m = respuesta(usuarioService.findById(u.getId()));
        m.put("mensaje", "Contraseña actualizada. Las demás sesiones abiertas se cerraron.");
        return ResponseEntity.ok(m);
    }

    private Map<String, Object> respuesta(Usuario u) {
        List<String> pendientes = acceso.pendientes(u);
        String rol = (u.getRol() != null && u.getRol().getNombre() != null) ? u.getRol().getNombre() : "";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("token", jwtService.generar(u, pendientes));
        m.put("id", u.getId());
        m.put("usuario", u.getUsername());
        m.put("rol", rol);
        m.put("pendientes", pendientes);
        return m;
    }

    private Usuario actual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof JwtUser j) || j.id() == null) return null;
        return usuarioService.findById(j.id());
    }

    private static ResponseEntity<Map<String, Object>> noAutenticado() {
        return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
    }

    private static String ip(HttpServletRequest http) {
        String reenviado = http.getHeader("X-Forwarded-For");
        return reenviado != null && !reenviado.isBlank() ? reenviado.split(",")[0].trim() : http.getRemoteAddr();
    }
}

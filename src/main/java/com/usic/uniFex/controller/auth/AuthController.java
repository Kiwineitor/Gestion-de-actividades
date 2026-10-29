package com.usic.uniFex.controller.auth;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.IService.IUsuarioService;
import com.usic.uniFex.model.entity.Usuario;
import com.usic.uniFex.model.service.GestionPersonalApoyoService;
import com.usic.uniFex.model.service.MantenimientoService;
import com.usic.uniFex.security.JwtService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final IUsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MantenimientoService mantenimientoService;
    private final GestionPersonalApoyoService personalApoyo;

    public record LoginRequest(String usuario, String contrasena) {
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest req) {
        Usuario u = usuarioService.findByUsername(req.usuario()).orElse(null);
        if (u == null || req.contrasena() == null || !passwordEncoder.matches(req.contrasena(), u.getPassword())) {
            return ResponseEntity.status(401)
                    .body(Map.<String, Object>of("ok", false, "mensaje", "Usuario o contrasena incorrectos"));
        }
        String estado = u.getEstado();
        if (estado != null && !"ACTIVO".equalsIgnoreCase(estado.trim())) {
            return ResponseEntity.status(403)
                    .body(Map.<String, Object>of("ok", false, "mensaje", "Usuario inactivo"));
        }
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
        return ResponseEntity.ok(Map.<String, Object>of(
                "ok", true,
                "token", jwtService.generar(u),
                "id", u.getId(),
                "usuario", u.getUsername(),
                "rol", rol));
    }
}

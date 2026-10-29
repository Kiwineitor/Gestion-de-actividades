package com.usic.uniFex.controller.inscripcion;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.entity.Usuario;
import com.usic.uniFex.model.service.NotificacionService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
@Slf4j
public class NotificacionApiController {

    private final NotificacionService notificacionService;
    private final IUsuarioDao usuarioDao;

    @GetMapping("/notificaciones")
    public ResponseEntity<?> bandeja() {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Usuario u = usuarioDao.findById(usuarioId).orElse(null);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "Usuario no encontrado"));
        }
        return ResponseEntity.ok(notificacionService.getBandeja(u));
    }

    @GetMapping("/notificaciones/no-leidas")
    public ResponseEntity<?> noLeidas() {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Usuario u = usuarioDao.findById(usuarioId).orElse(null);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "Usuario no encontrado"));
        }
        return ResponseEntity.ok(notificacionService.getNoLeidas(u));
    }

    @GetMapping("/notificaciones/count")
    public ResponseEntity<?> countNoLeidas() {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Usuario u = usuarioDao.findById(usuarioId).orElse(null);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "Usuario no encontrado"));
        }
        return ResponseEntity.ok(Map.of("count", notificacionService.countNoLeidas(u)));
    }

    @PostMapping("/notificaciones/{id}/leer")
    public ResponseEntity<?> marcarLeida(@PathVariable Long id) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Usuario u = usuarioDao.findById(usuarioId).orElse(null);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "Usuario no encontrado"));
        }
        notificacionService.marcarLeida(id, u);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/notificaciones/leer-todas")
    public ResponseEntity<?> marcarTodasLeidas() {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Usuario u = usuarioDao.findById(usuarioId).orElse(null);
        if (u == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "Usuario no encontrado"));
        }
        notificacionService.marcarTodasLeidas(u);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/notificaciones/{id}/hilo")
    public ResponseEntity<?> hilo(@PathVariable Long id) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        return ResponseEntity.ok(notificacionService.getHilo(id));
    }

    @PostMapping("/notificaciones/{id}/responder")
    public ResponseEntity<?> responder(@PathVariable Long id,
                                       @RequestBody Map<String, String> body) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Usuario vendedor = usuarioDao.findById(usuarioId).orElse(null);
        if (vendedor == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "Usuario no encontrado"));
        }
        String respuesta = body != null ? body.get("respuesta") : null;
        if (respuesta == null || respuesta.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "La respuesta es obligatoria"));
        }
        notificacionService.responderObservacion(id, vendedor, respuesta.trim());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/notificaciones/{id}/resolver")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<?> resolver(@PathVariable Long id,
                                      @RequestBody Map<String, String> body,
                                      @RequestHeader(value = "X-Origen", required = false) String origen) {
        Long adminId = usuarioActual();
        if (adminId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Usuario admin = usuarioDao.findById(adminId).orElse(null);
        if (admin == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "Usuario no encontrado"));
        }
        String respuesta = body != null ? body.get("respuesta") : null;
        if (respuesta == null || respuesta.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "La respuesta es obligatoria"));
        }
        notificacionService.resolverHilo(id, admin, respuesta.trim());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/notificaciones/observacion")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<?> crearObservacion(@RequestBody Map<String, Object> body,
                                              @RequestHeader(value = "X-Origen", required = false) String origen) {
        Long adminId = usuarioActual();
        if (adminId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Usuario admin = usuarioDao.findById(adminId).orElse(null);
        if (admin == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "Usuario no encontrado"));
        }

        Long vendedorId = ((Number) body.get("vendedorId")).longValue();
        String asunto = (String) body.get("asunto");
        String cuerpo = (String) body.get("cuerpo");
        Long inscripcionId = body.get("inscripcionId") != null ? ((Number) body.get("inscripcionId")).longValue() : null;
        Long puestoId = body.get("puestoId") != null ? ((Number) body.get("puestoId")).longValue() : null;

        if (vendedorId == null || asunto == null || asunto.trim().isEmpty() || cuerpo == null || cuerpo.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "vendedorId, asunto y cuerpo son obligatorios"));
        }

        notificacionService.notificar(vendedorId, NotificacionService.TIPO_OBSERVACION_ADMIN,
                asunto.trim(), cuerpo.trim(), inscripcionId, puestoId);

        return ResponseEntity.ok(Map.of("ok", true));
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }
}
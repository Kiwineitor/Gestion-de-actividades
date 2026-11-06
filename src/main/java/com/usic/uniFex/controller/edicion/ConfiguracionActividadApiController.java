package com.usic.uniFex.controller.edicion;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.service.ConfiguracionActividadService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/ediciones")
@RequiredArgsConstructor
@PreAuthorize(Roles.GESTIONA_ACTIVIDADES)
public class ConfiguracionActividadApiController {

    private final ConfiguracionActividadService configuracion;

    public record Interruptor(Boolean valor) {
    }

    @PostMapping("/asistente")
    public ResponseEntity<Map<String, Object>> crear(@RequestBody ConfiguracionActividadService.Cambios cambios) {
        return responder(configuracion.crear(cambios, usuarioActual()));
    }

    @GetMapping("/{id}/configuracion")
    public ResponseEntity<Map<String, Object>> obtener(@PathVariable Long id) {
        Map<String, Object> datos = configuracion.obtener(id);
        return datos == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(datos);
    }

    @PatchMapping("/{id}/configuracion")
    public ResponseEntity<Map<String, Object>> guardar(@PathVariable Long id,
                                                       @RequestBody ConfiguracionActividadService.Cambios cambios) {
        return responder(configuracion.guardar(id, cambios, usuarioActual()));
    }

    @PostMapping("/{id}/publicar")
    public ResponseEntity<Map<String, Object>> publicar(@PathVariable Long id, @RequestBody Interruptor req) {
        return responder(configuracion.publicar(id, req != null && Boolean.TRUE.equals(req.valor())));
    }

    @PostMapping("/{id}/finalizar")
    public ResponseEntity<Map<String, Object>> finalizar(@PathVariable Long id, @RequestBody Interruptor req) {
        return responder(configuracion.finalizar(id, req != null && Boolean.TRUE.equals(req.valor())));
    }

    private ResponseEntity<Map<String, Object>> responder(ConfiguracionActividadService.Resultado r) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("id", r.id());
        return ResponseEntity.status(r.ok() ? 200 : 409).body(cuerpo);
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }
}

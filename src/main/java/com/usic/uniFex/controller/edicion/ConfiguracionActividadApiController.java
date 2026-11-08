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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.service.ActividadRespaldoService;
import com.usic.uniFex.model.service.ConfiguracionActividadService;
import com.usic.uniFex.model.service.PlantillaCredencialService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/ediciones")
@RequiredArgsConstructor
@PreAuthorize(Roles.GESTIONA_ACTIVIDADES)
public class ConfiguracionActividadApiController {

    private final ConfiguracionActividadService configuracion;
    private final PlantillaCredencialService plantillas;
    private final ActividadRespaldoService respaldos;

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

    @PostMapping("/{id}/estado")
    public ResponseEntity<Map<String, Object>> estado(@PathVariable Long id, @RequestBody ConfiguracionActividadService.CambioEstado req) {
        return responder(configuracion.cambiarEstado(id, req));
    }

    @PutMapping("/{id}/qr-estilo")
    public ResponseEntity<Map<String, Object>> qrEstilo(@PathVariable Long id, @RequestBody Map<String, Object> estilo) {
        return responder(configuracion.guardarQrEstilo(id, estilo));
    }

    @PostMapping("/{id}/duplicar")
    public ResponseEntity<Map<String, Object>> duplicar(@PathVariable Long id) {
        ActividadRespaldoService.Resultado r = respaldos.duplicar(id);
        return responder(new ConfiguracionActividadService.Resultado(r.ok(), r.mensaje(), r.id()));
    }

    @GetMapping("/papelera")
    public java.util.List<Map<String, Object>> papelera() {
        return respaldos.papelera();
    }

    @PostMapping("/papelera/{respaldo}/restaurar")
    public ResponseEntity<Map<String, Object>> restaurar(@PathVariable Long respaldo) {
        ActividadRespaldoService.Resultado r = respaldos.restaurar(respaldo);
        return responder(new ConfiguracionActividadService.Resultado(r.ok(), r.mensaje(), r.id()));
    }

    @PostMapping("/{id}/credencial/{rol}")
    public ResponseEntity<Map<String, Object>> credencial(@PathVariable Long id, @PathVariable String rol) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        try {
            PlantillaCredencialService.Fila f = plantillas.asegurarParaRol(id, rol == null ? null : rol.toUpperCase(java.util.Locale.ROOT), usuarioActual());
            cuerpo.put("ok", true);
            cuerpo.put("id", f.id());
            cuerpo.put("etiqueta", f.plantilla().etiqueta());
            return ResponseEntity.ok(cuerpo);
        } catch (PlantillaCredencialService.PlantillaInvalida e) {
            cuerpo.put("ok", false);
            cuerpo.put("mensaje", e.getMessage());
            return ResponseEntity.status(409).body(cuerpo);
        }
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

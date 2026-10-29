package com.usic.uniFex.controller.usuario;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dto.VendedorDTO;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.service.VendedorAsignacionService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class VendedorAsignacionApiController {

    private final VendedorAsignacionService service;

    @GetMapping("/vendedores")
    @PreAuthorize(Roles.ADMINISTRA)
    public List<VendedorDTO> listarVendedores() {
        return service.listarVendedores();
    }

    @GetMapping("/vendedores/{id}/puestos")
    @PreAuthorize(Roles.ADMINISTRA)
    public List<Puesto> getPuestos(@PathVariable Long id) {
        return service.getPuestosAsignados(id);
    }

    @GetMapping("/vendedores/puestos-asignables")
    @PreAuthorize(Roles.ADMINISTRA)
    public List<VendedorAsignacionService.CasetaAsignable> catalogoAsignable() {
        return service.catalogoAsignable();
    }

    @PutMapping("/vendedores/{id}/puestos")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<?> reemplazarPuestos(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long adminId = usuarioActual();
        if (adminId == null) return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        Object crudo = body == null ? null : body.get("puestoIds");
        List<Long> ids = (crudo instanceof List<?> l)
                ? l.stream().filter(o -> o instanceof Number).map(o -> ((Number) o).longValue()).toList()
                : List.of();
        try {
            VendedorAsignacionService.ResultadoAsignacion r = service.reemplazarPuestos(id, ids, adminId);
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("ok", true);
            cuerpo.put("asignadas", r.asignadas());
            cuerpo.put("quitadas", r.quitadas());
            cuerpo.put("noDisponibles", r.noDisponibles());
            cuerpo.put("mensaje", r.noDisponibles().isEmpty()
                    ? "Casetas habilitadas"
                    : r.noDisponibles().size() + " caseta(s) ya no existen y no se habilitaron");
            return ResponseEntity.ok(cuerpo);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", e.getMessage()));
        }
    }

    @PostMapping("/vendedores/areas/{areaId}/puestos")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<?> agregarPuestosAArea(@PathVariable Long areaId, @RequestBody Map<String, Object> body) {
        Long adminId = usuarioActual();
        if (adminId == null) return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        Object crudo = body == null ? null : body.get("puestoIds");
        List<Long> ids = (crudo instanceof List<?> l)
                ? l.stream().filter(o -> o instanceof Number).map(o -> ((Number) o).longValue()).toList()
                : List.of();
        try {
            VendedorAsignacionService.ResultadoAsignacionMasiva r = service.agregarPuestosAArea(areaId, ids, adminId);
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("ok", true);
            cuerpo.put("vendedores", r.vendedores());
            cuerpo.put("casetas", r.casetas());
            cuerpo.put("asignacionesNuevas", r.asignacionesNuevas());
            cuerpo.put("noDisponibles", r.noDisponibles());
            cuerpo.put("mensaje", "Se habilitaron " + r.casetas() + " caseta(s) a "
                    + r.vendedores() + " vendedor(es) de la facultad");
            return ResponseEntity.ok(cuerpo);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", e.getMessage()));
        }
    }

    @DeleteMapping("/vendedores/{id}/puestos/{puestoId}")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<?> quitarPuesto(@PathVariable Long id, @PathVariable Long puestoId) {
        service.quitarPuesto(id, puestoId);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/mis-puestos")
    public ResponseEntity<?> misPuestos() {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        return ResponseEntity.ok(service.getPuestosVisiblesParaVendedor(usuarioId));
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
    }
}

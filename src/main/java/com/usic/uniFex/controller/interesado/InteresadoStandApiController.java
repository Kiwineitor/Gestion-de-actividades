package com.usic.uniFex.controller.interesado;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
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

import com.usic.uniFex.model.dto.InteresadoStandDTO;
import com.usic.uniFex.model.dto.InteresadoStandRequest;
import com.usic.uniFex.model.service.CategoriaMapaService;
import com.usic.uniFex.model.service.InteresadoStandEventPublisher;
import com.usic.uniFex.model.service.InteresadoStandService;
import com.usic.uniFex.model.service.InteresadoStandService.Resultado;
import com.usic.uniFex.model.service.RegistroVentaService.CasetaNoDisponibleException;
import com.usic.uniFex.model.dao.IInteresadoStandDao;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/interesados-stand")
@RequiredArgsConstructor
@PreAuthorize(Roles.ATIENDE_INTERESADOS)
public class InteresadoStandApiController {

    private final InteresadoStandService servicio;
    private final CategoriaMapaService categorias;
    private final InteresadoStandEventPublisher publisher;
    private final IInteresadoStandDao interesados;

    @PostMapping("/{id}/aprobar-venta")
    public ResponseEntity<Map<String, Object>> aprobarComoVenta(@PathVariable Long id,
                                                                 @RequestBody InteresadoStandService.Aprobacion req) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        Resultado r;
        try {
            r = servicio.aprobarComoVenta(id, req, usuarioId);
        } catch (CasetaNoDisponibleException e) {
            return ResponseEntity.status(409).body(Map.of("ok", false, "mensaje",
                    "Ese espacio ya no está libre: alguien lo vendió o lo reservó. Elige otro."));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(409).body(Map.of("ok", false, "mensaje", "Ese espacio tiene otra solicitud vigente."));
        }
        if (r.ok()) publisher.publicar(r.interesado());
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("interesado", r.interesado() != null ? InteresadoStandDTO.de(r.interesado()) : null);
        cuerpo.put("entrega", r.ok() ? servicio.entrega(r.interesado()) : null);
        return r.ok() ? ResponseEntity.ok(cuerpo) : ResponseEntity.badRequest().body(cuerpo);
    }

    @GetMapping("/{id}/entrega")
    public ResponseEntity<Map<String, Object>> entrega(@PathVariable Long id) {
        Map<String, Object> e = interesados.findById(id).map(servicio::entrega).orElse(null);
        return e == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(e);
    }

    private static Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof JwtUser u ? u.id() : null;
    }

    @GetMapping
    public List<InteresadoStandDTO> listar() {
        return servicio.listar().stream().map(InteresadoStandDTO::de).toList();
    }

    @GetMapping("/categorias")
    public List<Map<String, Object>> categorias() {
        return categorias.listar().stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("nombre", c.getNombre());
                    m.put("color", c.getColor());
                    return m;
                })
                .toList();
    }

    public record CambioEstado(String estado) {
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<Map<String, Object>> cambiarEstado(@PathVariable Long id, @RequestBody CambioEstado req) {
        Resultado r;
        try {
            r = servicio.cambiarEstado(id, req == null ? null : req.estado());
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "Ese espacio ya tiene otra solicitud vigente."));
        }
        if (r.ok()) publisher.publicar(r.interesado());
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("interesado", r.interesado() != null ? InteresadoStandDTO.de(r.interesado()) : null);
        return r.ok() ? ResponseEntity.ok(cuerpo) : ResponseEntity.badRequest().body(cuerpo);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> editar(@PathVariable Long id, @RequestBody InteresadoStandRequest req) {
        Resultado r = servicio.editar(id, req);
        if (r.ok()) publisher.publicar(r.interesado());
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("interesado", r.interesado() != null ? InteresadoStandDTO.de(r.interesado()) : null);
        return r.ok() ? ResponseEntity.ok(cuerpo) : ResponseEntity.badRequest().body(cuerpo);
    }
}

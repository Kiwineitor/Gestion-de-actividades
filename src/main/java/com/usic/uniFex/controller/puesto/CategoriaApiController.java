package com.usic.uniFex.controller.puesto;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.CategoriaOpcion;
import com.usic.uniFex.model.service.CategoriaMapaService;
import com.usic.uniFex.model.service.CategoriaOpcionService;
import com.usic.uniFex.model.service.CategoriaMapaService.ResultadoAjuste;
import com.usic.uniFex.model.service.PuestoEventPublisher;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/categorias")
@RequiredArgsConstructor
@PreAuthorize(Roles.EDITA_CATEGORIAS)
public class CategoriaApiController {

    private final CategoriaMapaService service;
    private final CategoriaOpcionService opciones;
    private final PuestoEventPublisher publisher;
    private final IPuestoDao puestoDao;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, Object>> listar() {
        Map<Long, List<CategoriaOpcion>> porCategoria = opciones.porCategoria();
        return service.listar().stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("nombre", c.getNombre());
                    m.put("color", c.getColor());
                    m.put("forma", c.getForma());
                    m.put("tamanoMapa", c.getTamanoMapa());
                    m.put("mapaProporcion", c.getMapaProporcion() != null ? c.getMapaProporcion() : 1.0);
                    m.put("cantidad", puestoDao.idsActivosDeCategoria(c.getId()).size());
                    m.put("tamano", puestoDao.tamanosDeCategoria(c.getId()).stream().findFirst().orElse(null));
                    m.put("precioBase", c.getPrecioBase());
                    m.put("opciones", porCategoria.getOrDefault(c.getId(), List.of()).stream()
                            .map(CategoriaApiController::opcionComoMapa).toList());
                    return m;
                })
                .toList();
    }

    private static Map<String, Object> opcionComoMapa(CategoriaOpcion o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("nombre", o.getNombre());
        m.put("precio", o.getPrecio());
        m.put("predeterminada", Boolean.TRUE.equals(o.getPredeterminada()));
        m.put("orden", o.getOrden());
        return m;
    }

    @PostMapping("/{id}/opciones")
    public ResponseEntity<Map<String, Object>> crearOpcion(
            @PathVariable Long id, @RequestBody CategoriaOpcionService.CambioOpcion req) {
        try {
            CategoriaOpcion o = opciones.crear(id, req, usuarioActual());
            if (Boolean.TRUE.equals(o.getPredeterminada())) publisher.publicarVarios(puestoDao.idsActivosDeCategoria(id));
            return ResponseEntity.ok(Map.<String, Object>of("ok", true, "opcion", opcionComoMapa(o)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.<String, Object>of("ok", false, "mensaje", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/opciones/{opcionId}")
    public ResponseEntity<Map<String, Object>> actualizarOpcion(
            @PathVariable Long id, @PathVariable Long opcionId,
            @RequestBody CategoriaOpcionService.CambioOpcion req) {
        CategoriaOpcion o = opciones.actualizar(opcionId, req, usuarioActual());
        if (o == null) return ResponseEntity.status(404).body(Map.<String, Object>of("ok", false));
        if (Boolean.TRUE.equals(o.getPredeterminada())) publisher.publicarVarios(puestoDao.idsActivosDeCategoria(id));
        return ResponseEntity.ok(Map.<String, Object>of("ok", true, "opcion", opcionComoMapa(o)));
    }

    @DeleteMapping("/{id}/opciones/{opcionId}")
    public ResponseEntity<Map<String, Object>> eliminarOpcion(
            @PathVariable Long id, @PathVariable Long opcionId) {
        String motivo = opciones.eliminar(opcionId, usuarioActual());
        if (motivo == null) return ResponseEntity.status(404).body(Map.<String, Object>of("ok", false));
        if (!motivo.isEmpty()) {
            return ResponseEntity.status(409).body(Map.<String, Object>of("ok", false, "mensaje", motivo));
        }
        return ResponseEntity.ok(Map.<String, Object>of("ok", true));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody CategoriaMapaService.NuevaCategoria req) {
        Categoria c = service.crear(req, usuarioActual());
        opciones.asegurarPredeterminada(c.getId(), c.getNombre(), c.getPrecioBase(), usuarioActual());
        publisher.publicarVarios(puestoDao.idsActivosDeCategoria(c.getId()));
        return ResponseEntity.ok(Map.<String, Object>of("ok", true, "id", c.getId(), "nombre", c.getNombre()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(
            @PathVariable Long id, @RequestBody CategoriaMapaService.CambioCategoria cambio) {
        Categoria c = service.actualizar(id, cambio);
        if (c == null) return ResponseEntity.status(404).body(Map.<String, Object>of("ok", false));
        publisher.publicarVarios(puestoDao.idsActivosDeCategoria(id));
        return ResponseEntity.ok(Map.<String, Object>of("ok", true));
    }

    @PatchMapping("/{id}/cantidad")
    public ResponseEntity<Map<String, Object>> ajustarCantidad(
            @PathVariable Long id, @RequestBody CantidadRequest req) {
        if (req.cantidad() == null || req.cantidad() < 0) {
            return ResponseEntity.status(400).body(Map.<String, Object>of("ok", false, "mensaje", "Cantidad invalida"));
        }
        ResultadoAjuste r = service.ajustarCantidad(id, req.cantidad(), usuarioActual());
        if (r == null) return ResponseEntity.status(404).body(Map.<String, Object>of("ok", false, "mensaje", "Categoria inexistente"));
        publisher.publicarVarios(r.afectados());
        return ResponseEntity.ok(Map.<String, Object>of(
                "ok", true, "creadas", r.creadas(), "anuladas", r.anuladas(), "noQuitadas", r.noQuitadas()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminar(@PathVariable Long id) {
        List<Long> anuladas = service.eliminar(id, usuarioActual());
        if (anuladas == null) {
            return ResponseEntity.status(409).body(Map.<String, Object>of(
                    "ok", false, "mensaje", "No se puede eliminar: la categoria tiene casetas vendidas o reservadas"));
        }
        publisher.publicarVarios(anuladas);
        return ResponseEntity.ok(Map.<String, Object>of("ok", true, "eliminadas", anuladas.size()));
    }

    public record CantidadRequest(Integer cantidad) {
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
    }
}

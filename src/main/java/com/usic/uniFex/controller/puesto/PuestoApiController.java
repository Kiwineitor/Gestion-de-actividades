package com.usic.uniFex.controller.puesto;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.dto.AsignacionPuestoDTO;
import com.usic.uniFex.model.dto.OcupacionPuestoDTO;
import com.usic.uniFex.model.dto.PuestoEstadoDTO;
import com.usic.uniFex.model.dto.PuestoFotoDTO;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.service.PuestoEventPublisher;
import com.usic.uniFex.model.service.PuestoFotoService;
import com.usic.uniFex.model.service.PuestoMapaService;
import com.usic.uniFex.model.service.PuestoOcupacionService;
import com.usic.uniFex.model.service.PuestoReservaService;
import com.usic.uniFex.model.service.VendedorAsignacionService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/puestos")
@RequiredArgsConstructor
public class PuestoApiController {

    private final PuestoReservaService reservaService;
    private final PuestoEventPublisher publisher;
    private final PuestoMapaService mapaService;
    private final PuestoFotoService fotoService;
    private final IPuestoDao puestoDao;
    private final VendedorAsignacionService vendedorAsignacionService;
    private final PuestoOcupacionService ocupacionService;

    @GetMapping
    public List<PuestoEstadoDTO> listar(@RequestParam(value = "categoriaId", required = false) Long categoriaId) {
        if (usuarioActual() == null) return List.of();
        return puestoDao.listarActivos().stream()
                .filter(p -> categoriaId == null
                        || (p.getCategoria() != null && categoriaId.equals(p.getCategoria().getId())))
                .map(PuestoEstadoDTO::de)
                .toList();
    }

    @GetMapping("/asignaciones")
    public List<AsignacionPuestoDTO> asignaciones() {
        return usuarioActual() == null ? List.of() : vendedorAsignacionService.asignacionesConVendedor();
    }

    @GetMapping("/ocupacion")
    public List<OcupacionPuestoDTO> ocupacion() {
        return usuarioActual() == null ? List.of() : ocupacionService.ocupacionConVendedor();
    }

    @PostMapping("/{id}/reservar")
    public ResponseEntity<Map<String, Object>> reservar(@PathVariable Long id) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return noAutenticado();
        ResponseEntity<Map<String, Object>> veto = vetoPorAsignacion(usuarioId, id);
        if (veto != null) return veto;
        boolean ok = reservaService.reservar(id, usuarioId);
        if (ok) publisher.publicar(id);
        return respuesta(ok, ok ? "Caseta reservada" : "La caseta ya no esta disponible");
    }

    @PostMapping("/{id}/liberar")
    public ResponseEntity<Map<String, Object>> liberar(@PathVariable Long id) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return noAutenticado();
        boolean ok = reservaService.liberar(id, usuarioId);
        if (ok) publisher.publicar(id);
        return respuesta(ok, ok ? "Reserva liberada" : "No tienes esta caseta reservada");
    }

    public record LotePuestos(List<Long> ids) {
    }

    @GetMapping("/mi-carrito")
    public ResponseEntity<Map<String, Object>> miCarrito() {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return noAutenticado();

        List<PuestoEstadoDTO> items = reservaService.carritoDe(usuarioId).stream()
                .map(PuestoEstadoDTO::de).toList();
        BigDecimal total = items.stream()
                .map(i -> i.precio() != null ? i.precio() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", true);
        cuerpo.put("cantidad", items.size());
        cuerpo.put("total", total);
        cuerpo.put("items", items);
        return ResponseEntity.ok(cuerpo);
    }

    @PostMapping("/carrito")
    public ResponseEntity<Map<String, Object>> agregarAlCarrito(@RequestBody LotePuestos req) {
        return operarCarrito(req, true);
    }

    @DeleteMapping("/carrito")
    public ResponseEntity<Map<String, Object>> quitarDelCarrito(@RequestBody LotePuestos req) {
        return operarCarrito(req, false);
    }

    private ResponseEntity<Map<String, Object>> vetoPorAsignacion(Long usuarioId, Long puestoId) {
        if (!esVendedor() || vendedorAsignacionService.puedeVender(usuarioId, puestoId)) return null;
        return ResponseEntity.status(409).body(Map.<String, Object>of(
                "ok", false, "mensaje", "Esta caseta no esta asignada a ti"));
    }

    private boolean esVendedor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMINISTRATIVO".equals(a.getAuthority()));
    }

    private ResponseEntity<Map<String, Object>> operarCarrito(LotePuestos req, boolean agregar) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return noAutenticado();
        if (req == null || req.ids() == null || req.ids().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "mensaje", "No se indico ninguna caseta"));
        }

        List<Long> ids = req.ids();
        List<Long> noPermitidas = List.of();
        if (agregar && esVendedor()) {
            noPermitidas = vendedorAsignacionService.casetasNoPermitidas(usuarioId, ids);
            if (!noPermitidas.isEmpty()) {
                List<Long> permitidas = new java.util.ArrayList<>(ids);
                permitidas.removeAll(noPermitidas);
                ids = permitidas;
            }
        }
        if (ids.isEmpty()) {
            return ResponseEntity.status(403).body(Map.of(
                    "ok", false,
                    "logradas", List.of(),
                    "rechazadas", noPermitidas,
                    "mensaje", "Esas casetas no estan asignadas a ti"));
        }

        PuestoReservaService.ResultadoLote r = agregar
                ? reservaService.agregarAlCarrito(ids, usuarioId)
                : reservaService.quitarDelCarrito(ids, usuarioId);

        publisher.publicarVarios(r.logradas());

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        List<Long> rechazadas = new java.util.ArrayList<>(r.rechazadas());
        rechazadas.addAll(noPermitidas);
        cuerpo.put("ok", r.todoOk() && noPermitidas.isEmpty());
        cuerpo.put("logradas", r.logradas());
        cuerpo.put("rechazadas", rechazadas);
        cuerpo.put("mensaje", r.todoOk()
                ? (agregar ? "Casetas agregadas" : "Casetas liberadas")
                : (agregar
                    ? "Algunas casetas ya no estaban disponibles: " + r.rechazadas().size()
                    : "Algunas casetas no eran tuyas: " + r.rechazadas().size()));
        return ResponseEntity.ok(cuerpo);
    }

    @GetMapping("/{id}/fotos")
    public List<PuestoFotoDTO> fotos(@PathVariable Long id) {
        return fotoService.fotosDe(id).stream().map(PuestoFotoDTO::de).toList();
    }

    @GetMapping("/con-foto")
    public List<Long> idsConFoto() {
        return fotoService.idsConFoto();
    }

    @PostMapping(value = "/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> subirFoto(
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam("puestos") List<Long> puestos,
            @RequestParam(value = "descripcion", required = false) String descripcion) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return noAutenticado();

        PuestoFotoService.Resultado r = fotoService.subir(puestos, archivo, descripcion, usuarioId);
        return r.ok()
                ? ResponseEntity.ok(Map.of("ok", true, "mensaje", r.mensaje(),
                        "puestos", r.puestosAfectados()))
                : ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", r.mensaje()));
    }

    @DeleteMapping("/fotos/{fotoId}")
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> borrarFoto(@PathVariable Long fotoId) {
        boolean ok = fotoService.borrar(fotoId);
        return respuesta(ok, ok ? "Foto eliminada" : "La foto no existe");
    }

    public record ReferenciaRequest(String referencia) {
    }

    @PatchMapping("/{id}/referencia")
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> referencia(
            @PathVariable Long id, @RequestBody ReferenciaRequest req) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return noAutenticado();
        boolean ok = mapaService.cambiarReferencia(id, req.referencia(), usuarioId);
        if (ok) publisher.publicar(id);
        return respuesta(ok, ok ? "Referencia guardada" : "La caseta no existe o esta anulada");
    }

    @GetMapping("/{id}/venta")
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> venta(@PathVariable Long id) {
        PuestoMapaService.VentaDePuesto v = mapaService.ventaVigente(id);
        Map<String, Object> cuerpo = new java.util.LinkedHashMap<>();
        cuerpo.put("ok", true);
        cuerpo.put("venta", v);
        return ResponseEntity.ok(cuerpo);
    }

    @GetMapping("/pagados")
    @PreAuthorize(Roles.EDITA_PLANO)
    public List<Long> pagados() {
        return mapaService.pagados();
    }

    public record CambioCategoria(Long categoriaId) {
    }

    @PatchMapping("/{id}/categoria")
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> cambiarCategoria(
            @PathVariable Long id, @RequestBody CambioCategoria req) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return noAutenticado();
        String error = mapaService.cambiarCategoria(id, req.categoriaId(), usuarioId);
        if (error == null) publisher.publicar(id);
        return respuesta(error == null, error == null ? "Categoría cambiada" : error);
    }

    @PatchMapping("/{id}/ficha")
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> ficha(
            @PathVariable Long id, @RequestBody PuestoMapaService.CambioFicha req) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return noAutenticado();
        boolean ok = mapaService.actualizarFicha(id, req, usuarioId);
        if (ok) publisher.publicar(id);
        return respuesta(ok, ok ? "Caseta actualizada" : "La caseta no existe o esta anulada");
    }

    @PostMapping("/{id}/confirmar")
    public ResponseEntity<Map<String, Object>> confirmar(@PathVariable Long id) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return noAutenticado();
        ResponseEntity<Map<String, Object>> veto = vetoPorAsignacion(usuarioId, id);
        if (veto != null) return veto;
        boolean ok = reservaService.confirmar(id, usuarioId);
        if (ok) publisher.publicar(id);
        return respuesta(ok, ok ? "Venta confirmada" : "No se pudo confirmar (reserva no vigente)");
    }

    @PostMapping("/posiciones")
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> guardarPosiciones(
            @RequestBody List<PuestoMapaService.Posicion> posiciones) {
        List<Long> cambiados = mapaService.guardarPosiciones(posiciones);
        publisher.publicarVarios(cambiados);
        return ResponseEntity.ok(Map.<String, Object>of("ok", true, "guardadas", cambiados.size()));
    }

    @PostMapping
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> crear(@RequestBody NuevaCaseta req) {
        Puesto p = mapaService.crear(req.categoriaId(), req.codigo(), req.tamano(), usuarioActual());
        if (p == null) {
            return ResponseEntity.status(404).body(Map.<String, Object>of("ok", false, "mensaje", "Categoria inexistente"));
        }
        publisher.publicar(p.getId());
        return ResponseEntity.ok(Map.<String, Object>of("ok", true, "id", p.getId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> anular(@PathVariable Long id) {
        boolean ok = mapaService.anular(id, usuarioActual());
        if (ok) publisher.publicar(id);
        return respuesta(ok, ok ? "Caseta eliminada" : "Solo se puede eliminar una caseta libre y sin ventas");
    }

    public record NuevaCaseta(Long categoriaId, String codigo, String tamano) {
    }

    @PatchMapping("/codigos")
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> renumerar(
            @RequestBody List<PuestoMapaService.Codigo> cambios) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return noAutenticado();

        PuestoMapaService.ResultadoRenumeracion r = mapaService.renumerar(cambios, usuarioId);
        if (r.ok()) publisher.publicarVarios(r.cambiados());
        return ResponseEntity.status(r.ok() ? 200 : 409).body(Map.<String, Object>of(
                "ok", r.ok(), "mensaje", r.mensaje(), "cambiados", r.cambiados().size()));
    }

    @PatchMapping("/precios")
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> actualizarPrecios(
            @RequestBody List<PuestoMapaService.PrecioCaseta> cambios) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return noAutenticado();

        PuestoMapaService.ResultadoRenumeracion r = mapaService.actualizarPrecios(cambios, usuarioId);
        if (r.ok()) publisher.publicarVarios(r.cambiados());
        return ResponseEntity.status(r.ok() ? 200 : 409).body(Map.<String, Object>of(
                "ok", r.ok(), "mensaje", r.mensaje(), "cambiados", r.cambiados().size()));
    }

    @PostMapping("/{id}/bloquear")
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> bloquear(@PathVariable Long id) {
        boolean ok = mapaService.bloquear(id, usuarioActual());
        if (ok) publisher.publicar(id);
        return respuesta(ok, ok ? "Caseta bloqueada (reparacion)" : "Solo se bloquea una caseta libre");
    }

    @PostMapping("/{id}/desbloquear")
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> desbloquear(@PathVariable Long id) {
        boolean ok = mapaService.desbloquear(id, usuarioActual());
        if (ok) publisher.publicar(id);
        return respuesta(ok, ok ? "Caseta desbloqueada" : "La caseta no esta bloqueada o esta anulada");
    }

    private Long usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
    }

    private ResponseEntity<Map<String, Object>> respuesta(boolean ok, String mensaje) {
        return ResponseEntity.status(ok ? 200 : 409)
                .body(Map.<String, Object>of("ok", ok, "mensaje", mensaje));
    }

    private ResponseEntity<Map<String, Object>> noAutenticado() {
        return ResponseEntity.status(401).body(Map.<String, Object>of("ok", false, "mensaje", "No autenticado"));
    }
}

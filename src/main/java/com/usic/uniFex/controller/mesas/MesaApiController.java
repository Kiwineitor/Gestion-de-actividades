package com.usic.uniFex.controller.mesas;

import java.io.IOException;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.service.MesaService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/mesas")
@RequiredArgsConstructor
@PreAuthorize(Roles.GESTIONA_MESAS)
public class MesaApiController {

    private final MesaService mesas;

    public record Motivo(String motivo) {
    }

    public record Codigo(String codigo) {
    }

    public record Bloqueo(Boolean fueraDeVenta) {
    }

    @GetMapping("/eventos")
    public List<Map<String, Object>> eventos() {
        return mesas.listarEventos();
    }

    @GetMapping("/eventos/{id}")
    public ResponseEntity<Map<String, Object>> evento(@PathVariable Long id) {
        Map<String, Object> e = mesas.detalleEvento(id);
        return e == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(e);
    }

    @PostMapping("/eventos")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<Map<String, Object>> crear(@RequestBody MesaService.EventoDatos datos) {
        return MesaPublicaController.responder(mesas.crearEvento(datos, usuario()));
    }

    @PatchMapping("/eventos/{id}")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<Map<String, Object>> editar(@PathVariable Long id, @RequestBody MesaService.EventoDatos datos) {
        return MesaPublicaController.responder(mesas.editarEvento(id, datos, usuario()));
    }

    @PostMapping(value = "/eventos/{id}/plano", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<Map<String, Object>> plano(@PathVariable Long id, @RequestParam("archivo") MultipartFile archivo)
            throws IOException {
        return MesaPublicaController.responder(mesas.subirImagenEvento(id, archivo, true));
    }

    @PostMapping(value = "/eventos/{id}/qr-pago", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<Map<String, Object>> qrPago(@PathVariable Long id, @RequestParam("archivo") MultipartFile archivo)
            throws IOException {
        return MesaPublicaController.responder(mesas.subirImagenEvento(id, archivo, false));
    }

    @DeleteMapping("/eventos/{id}/qr-pago")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<Map<String, Object>> quitarQrPago(@PathVariable Long id) {
        return MesaPublicaController.responder(mesas.quitarQrPago(id));
    }

    @PutMapping("/eventos/{id}/mesas")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<Map<String, Object>> guardarMesas(@PathVariable Long id, @RequestBody List<MesaService.MesaDatos> lista) {
        return MesaPublicaController.responder(mesas.guardarMesas(id, lista, usuario()));
    }

    @DeleteMapping("/{mesaId}")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<Map<String, Object>> eliminarMesa(@PathVariable Long mesaId) {
        return MesaPublicaController.responder(mesas.eliminarMesa(mesaId, usuario()));
    }

    @PatchMapping("/{mesaId}/venta")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<Map<String, Object>> fueraDeVenta(@PathVariable Long mesaId, @RequestBody Bloqueo req) {
        return MesaPublicaController.responder(mesas.fueraDeVenta(mesaId, req != null && Boolean.TRUE.equals(req.fueraDeVenta()), usuario()));
    }

    @GetMapping("/eventos/{id}/reservas")
    public List<Map<String, Object>> reservas(@PathVariable Long id, @RequestParam(required = false) String estado) {
        return mesas.reservas(id, estado);
    }

    @PostMapping("/reservas/{id}/confirmar")
    public ResponseEntity<Map<String, Object>> confirmar(@PathVariable Long id) {
        return MesaPublicaController.responder(mesas.confirmar(id, usuario()));
    }

    @PostMapping("/reservas/{id}/rechazar")
    public ResponseEntity<Map<String, Object>> rechazar(@PathVariable Long id, @RequestBody Motivo req) {
        return MesaPublicaController.responder(mesas.rechazar(id, req == null ? null : req.motivo(), usuario()));
    }

    @PostMapping("/reservas/{id}/no-presentado")
    public ResponseEntity<Map<String, Object>> noPresentado(@PathVariable Long id) {
        return MesaPublicaController.responder(mesas.liberarNoPresentado(id, usuario()));
    }

    @PostMapping("/ingreso")
    public ResponseEntity<Map<String, Object>> ingreso(@RequestBody Codigo req) {
        MesaService.Resultado r = mesas.registrarIngreso(req == null ? null : req.codigo(), usuario());
        Map<String, Object> cuerpo = new java.util.LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("reserva", r.datos());
        return ResponseEntity.ok(cuerpo);
    }

    private Long usuario() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }
}

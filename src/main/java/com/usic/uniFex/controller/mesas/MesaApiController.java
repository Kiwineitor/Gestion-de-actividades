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

import com.usic.uniFex.model.service.FileStorageService;
import com.usic.uniFex.model.service.MesaPaseService;
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
    private final MesaPaseService pases;
    private final FileStorageService archivos;

    public record Motivo(String motivo) {
    }

    public record Codigo(String codigo) {
    }

    public record Bloqueo(Boolean fueraDeVenta) {
    }

    public record Aprobacion(String tipoPase, Integer personas) {
    }

    public record Publicacion(Boolean publicado) {
    }

    public record VentaOficina(String nombre, String apellidos, String ci, String celular, String correo,
                               Integer personas, String tipoPase) {
    }

    public record Reservas(List<Long> reservas) {
    }

    @DeleteMapping("/eventos/{id}")
    @PreAuthorize(Roles.DISENA_MESAS)
    public ResponseEntity<Map<String, Object>> eliminarEvento(@PathVariable Long id) {
        return MesaPublicaController.responder(mesas.eliminarEvento(id, usuario()));
    }

    @PostMapping("/eventos/{eventoId}/mesas/{mesaId}/venta")
    @PreAuthorize(Roles.VENDE_MESAS)
    public ResponseEntity<Map<String, Object>> venderEnOficina(@PathVariable Long eventoId, @PathVariable Long mesaId,
                                                               @RequestBody VentaOficina req) {
        if (req == null) return MesaPublicaController.responder(new MesaService.Resultado(false, "Faltan los datos del cliente", null));
        return MesaPublicaController.responder(mesas.venderEnOficina(eventoId, mesaId,
                new MesaService.Cliente(req.nombre(), req.apellidos(), req.ci(), req.celular(), req.correo(), req.personas()),
                req.tipoPase(), usuario()));
    }

    @GetMapping("/reservas/{id}/entrega")
    @PreAuthorize(Roles.VENDE_MESAS)
    public ResponseEntity<Map<String, Object>> entrega(@PathVariable Long id) {
        Map<String, Object> e = mesas.entrega(id);
        return e == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(e);
    }

    @GetMapping(value = "/reservas/{id}/pases/{numero}/imagen", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize(Roles.VENDE_MESAS)
    public ResponseEntity<byte[]> imagenPase(@PathVariable Long id, @PathVariable int numero) {
        byte[] png = pases.imagen(id, numero);
        if (png == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .header("Content-Disposition", "inline; filename=\"pase-" + id + "-" + numero + ".png\"")
                .body(png);
    }

    @GetMapping(value = "/reservas/{id}/recibo", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(Roles.VENDE_MESAS)
    public ResponseEntity<byte[]> recibo(@PathVariable Long id) {
        byte[] pdf = pases.recibo(id);
        if (pdf == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"recibo-reserva-" + id + ".pdf\"")
                .body(pdf);
    }

    @PostMapping("/reservas/{id}/pases")
    @PreAuthorize(Roles.VENDE_MESAS)
    public ResponseEntity<Map<String, Object>> regenerarPases(@PathVariable Long id, @RequestBody(required = false) Aprobacion req) {
        return MesaPublicaController.responder(mesas.regenerarPases(id, req == null ? null : req.personas(),
                req == null ? null : req.tipoPase(), usuario()));
    }

    @PostMapping(value = "/pases/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(Roles.VALIDA_MESAS)
    public ResponseEntity<byte[]> pdfDeReservas(@RequestBody(required = false) Reservas req) {
        byte[] pdf = pases.pdfDeReservas(req == null ? null : req.reservas());
        if (pdf == null) {
            return ResponseEntity.status(409).contentType(MediaType.TEXT_PLAIN)
                    .body("No hay pases de reservas confirmadas para imprimir.".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"pases.pdf\"")
                .body(pdf);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(MesaService.Rechazo.class)
    public ResponseEntity<Map<String, Object>> rechazo(MesaService.Rechazo e) {
        return MesaPublicaController.responder(new MesaService.Resultado(false, e.getMessage(), null));
    }

    @PostMapping("/eventos/{id}/publicar")
    @PreAuthorize(Roles.DISENA_MESAS)
    public ResponseEntity<Map<String, Object>> publicar(@PathVariable Long id, @RequestBody Publicacion req) {
        return MesaPublicaController.responder(mesas.publicar(id, req != null && Boolean.TRUE.equals(req.publicado()), usuario()));
    }

    @GetMapping("/reservas/pendientes")
    @PreAuthorize(Roles.VALIDA_MESAS)
    public List<Map<String, Object>> pendientes() {
        return mesas.reservasPendientes();
    }

    @GetMapping(value = "/reservas/{codigo}/pases.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(Roles.VALIDA_MESAS)
    public ResponseEntity<byte[]> pasesPdf(@PathVariable String codigo) {
        return MesaPublicaController.pdfDePases(pases, codigo);
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
    @PreAuthorize(Roles.DISENA_MESAS)
    public ResponseEntity<Map<String, Object>> crear(@RequestBody MesaService.EventoDatos datos) {
        return MesaPublicaController.responder(mesas.crearEvento(datos, usuario()));
    }

    @PatchMapping("/eventos/{id}")
    @PreAuthorize(Roles.DISENA_MESAS)
    public ResponseEntity<Map<String, Object>> editar(@PathVariable Long id, @RequestBody MesaService.EventoDatos datos) {
        return MesaPublicaController.responder(mesas.editarEvento(id, datos, usuario()));
    }

    @PostMapping(value = "/eventos/{id}/plano", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.DISENA_MESAS)
    public ResponseEntity<Map<String, Object>> plano(@PathVariable Long id, @RequestParam("archivo") MultipartFile archivo)
            throws IOException {
        return MesaPublicaController.responder(mesas.subirImagenEvento(id, archivo, true));
    }

    @PostMapping(value = "/eventos/{id}/qr-pago", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.DISENA_MESAS)
    public ResponseEntity<Map<String, Object>> qrPago(@PathVariable Long id, @RequestParam("archivo") MultipartFile archivo)
            throws IOException {
        return MesaPublicaController.responder(mesas.subirImagenEvento(id, archivo, false));
    }

    @DeleteMapping("/eventos/{id}/plano")
    @PreAuthorize(Roles.DISENA_MESAS)
    public ResponseEntity<Map<String, Object>> quitarPlano(@PathVariable Long id) {
        return MesaPublicaController.responder(mesas.quitarPlano(id));
    }

    @PostMapping(value = "/eventos/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.DISENA_MESAS)
    public ResponseEntity<Map<String, Object>> logo(@PathVariable Long id, @RequestParam("archivo") MultipartFile archivo)
            throws IOException {
        return MesaPublicaController.responder(mesas.subirLogo(id, archivo));
    }

    @DeleteMapping("/eventos/{id}/logo")
    @PreAuthorize(Roles.DISENA_MESAS)
    public ResponseEntity<Map<String, Object>> quitarLogo(@PathVariable Long id) {
        return MesaPublicaController.responder(mesas.quitarLogo(id));
    }

    public record Persona(String nombre, String ci, Boolean quitarFoto) {
    }

    @PatchMapping("/reservas/{id}/pases/{numero}")
    @PreAuthorize(Roles.VENDE_MESAS)
    public ResponseEntity<Map<String, Object>> persona(@PathVariable Long id, @PathVariable int numero, @RequestBody Persona req) {
        String error = pases.actualizarPersona(id, numero, req == null ? null : req.nombre(),
                req == null ? null : req.ci(), req != null && Boolean.TRUE.equals(req.quitarFoto()));
        return MesaPublicaController.responder(error == null
                ? new MesaService.Resultado(true, "Datos del pase guardados", Map.of())
                : new MesaService.Resultado(false, error, null));
    }

    @PostMapping(value = "/reservas/{id}/pases/{numero}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.VENDE_MESAS)
    public ResponseEntity<Map<String, Object>> fotoPase(@PathVariable Long id, @PathVariable int numero,
                                                        @RequestParam("archivo") MultipartFile archivo) throws IOException {
        String tipo = String.valueOf(archivo == null ? "" : archivo.getContentType());
        if (archivo == null || archivo.isEmpty() || !(tipo.equals("image/png") || tipo.equals("image/jpeg"))) {
            return MesaPublicaController.responder(new MesaService.Resultado(false, "La foto debe ser PNG o JPG", null));
        }
        if (archivo.getSize() > 8L * 1024 * 1024) {
            return MesaPublicaController.responder(new MesaService.Resultado(false, "La foto no puede pasar de 8 MB", null));
        }
        String ruta = archivos.save(archivo, FileStorageService.Bucket.MESAS, "pase-" + id + "-" + numero);
        boolean ok = pases.guardarFoto(id, numero, ruta);
        return MesaPublicaController.responder(ok
                ? new MesaService.Resultado(true, "Foto guardada", Map.of("url", "/files/" + ruta))
                : new MesaService.Resultado(false, "El pase no existe", null));
    }

    @DeleteMapping("/eventos/{id}/qr-pago")
    @PreAuthorize(Roles.DISENA_MESAS)
    public ResponseEntity<Map<String, Object>> quitarQrPago(@PathVariable Long id) {
        return MesaPublicaController.responder(mesas.quitarQrPago(id));
    }

    public record Forma(Integer ancho, Integer alto, Boolean conservar) {
    }

    @PatchMapping("/eventos/{id}/forma")
    @PreAuthorize(Roles.DISENA_MESAS)
    public ResponseEntity<Map<String, Object>> cambiarForma(@PathVariable Long id, @RequestBody Forma req) {
        return MesaPublicaController.responder(mesas.cambiarForma(id, req.ancho(), req.alto(), !Boolean.FALSE.equals(req.conservar())));
    }

    @PutMapping("/eventos/{id}/elementos")
    @PreAuthorize(Roles.DISENA_MESAS)
    public ResponseEntity<Map<String, Object>> guardarElementos(@PathVariable Long id, @RequestBody List<MesaService.Elemento> lista) {
        return MesaPublicaController.responder(mesas.guardarElementos(id, lista));
    }

    @PutMapping("/eventos/{id}/mesas")
    @PreAuthorize(Roles.DISENA_MESAS)
    public ResponseEntity<Map<String, Object>> guardarMesas(@PathVariable Long id, @RequestBody List<MesaService.MesaDatos> lista) {
        return MesaPublicaController.responder(mesas.guardarMesas(id, lista, usuario()));
    }

    @DeleteMapping("/{mesaId}")
    @PreAuthorize(Roles.DISENA_MESAS)
    public ResponseEntity<Map<String, Object>> eliminarMesa(@PathVariable Long mesaId) {
        return MesaPublicaController.responder(mesas.eliminarMesa(mesaId, usuario()));
    }

    @PatchMapping("/{mesaId}/venta")
    @PreAuthorize(Roles.DISENA_MESAS)
    public ResponseEntity<Map<String, Object>> fueraDeVenta(@PathVariable Long mesaId, @RequestBody Bloqueo req) {
        return MesaPublicaController.responder(mesas.fueraDeVenta(mesaId, req != null && Boolean.TRUE.equals(req.fueraDeVenta()), usuario()));
    }

    @PreAuthorize(Roles.VALIDA_MESAS)
    @GetMapping("/eventos/{id}/reservas")
    public List<Map<String, Object>> reservas(@PathVariable Long id, @RequestParam(required = false) String estado) {
        return mesas.reservas(id, estado);
    }

    @PreAuthorize(Roles.VALIDA_MESAS)
    @PostMapping("/reservas/{id}/confirmar")
    public ResponseEntity<Map<String, Object>> confirmar(@PathVariable Long id, @RequestBody(required = false) Aprobacion req) {
        return MesaPublicaController.responder(mesas.confirmar(id, req == null ? null : req.tipoPase(),
                req == null ? null : req.personas(), usuario()));
    }

    @PreAuthorize(Roles.VALIDA_MESAS)
    @PostMapping("/reservas/{id}/rechazar")
    public ResponseEntity<Map<String, Object>> rechazar(@PathVariable Long id, @RequestBody Motivo req) {
        return MesaPublicaController.responder(mesas.rechazar(id, req == null ? null : req.motivo(), usuario()));
    }

    @PreAuthorize(Roles.VALIDA_MESAS)
    @PostMapping("/reservas/{id}/no-presentado")
    public ResponseEntity<Map<String, Object>> noPresentado(@PathVariable Long id) {
        return MesaPublicaController.responder(mesas.liberarNoPresentado(id, usuario()));
    }

    @PreAuthorize(Roles.REGISTRA_INGRESO)
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

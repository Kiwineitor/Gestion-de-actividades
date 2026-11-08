package com.usic.uniFex.controller.edicion;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.service.EntradaService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/entradas")
@RequiredArgsConstructor
public class EntradaApiController {

    private final EntradaService entradas;
    private final com.usic.uniFex.model.service.EntradaEnvioService envios;

    public record Envio(Boolean alExtra) {
    }

    public record Motivo(String motivo) {
    }

    public record Codigo(String codigo, String sentido) {
    }

    @GetMapping
    @PreAuthorize(Roles.VALIDA_ENTRADAS + " or " + Roles.VENDE_MESAS)
    public List<Map<String, Object>> listar(@RequestParam Long edicion, @RequestParam(required = false) String estado) {
        return entradas.listar(edicion, estado);
    }

    @GetMapping("/pendientes")
    @PreAuthorize(Roles.VALIDA_ENTRADAS)
    public Map<String, Object> pendientes() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("revision", entradas.enRevision());
        r.put("ultima", entradas.ultimaEnRevision());
        return r;
    }

    @GetMapping("/resumen")
    @PreAuthorize(Roles.VALIDA_ENTRADAS + " or " + Roles.VENDE_MESAS)
    public Map<String, Object> resumen(@RequestParam Long edicion) {
        return entradas.resumen(edicion);
    }

    @PostMapping("/{id}/aprobar")
    @PreAuthorize(Roles.VALIDA_ENTRADAS)
    public ResponseEntity<Map<String, Object>> aprobar(@PathVariable Long id) {
        return responder(entradas.aprobar(id, usuarioActual()));
    }

    @PostMapping("/{id}/rechazar")
    @PreAuthorize(Roles.VALIDA_ENTRADAS)
    public ResponseEntity<Map<String, Object>> rechazar(@PathVariable Long id, @RequestBody Motivo req) {
        return responder(entradas.rechazar(id, req == null ? null : req.motivo(), usuarioActual()));
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize(Roles.VALIDA_ENTRADAS)
    public ResponseEntity<Map<String, Object>> anular(@PathVariable Long id, @RequestBody Motivo req) {
        return responder(entradas.anular(id, req == null ? null : req.motivo(), usuarioActual()));
    }

    @PostMapping(value = "/oficina/{edicion}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.VENDE_MESAS)
    public ResponseEntity<Map<String, Object>> venderEnOficina(@PathVariable Long edicion,
                                                               @RequestPart("datos") EntradaService.Compra datos,
                                                               @RequestPart(value = "comprobante", required = false) MultipartFile comprobante)
            throws IOException {
        return responder(entradas.venderEnOficina(edicion, datos, comprobante, usuarioActual()));
    }

    @PostMapping("/ingreso")
    @PreAuthorize(Roles.INGRESO_ENTRADAS)
    public Map<String, Object> ingreso(@RequestBody Codigo req) {
        return entradas.registrarIngreso(req == null ? null : req.codigo(), req == null ? null : req.sentido(), usuarioActual());
    }

    @GetMapping("/compra/{codigo}/pases.pdf")
    @PreAuthorize(Roles.VALIDA_ENTRADAS + " or " + Roles.VENDE_MESAS)
    public ResponseEntity<byte[]> pases(@PathVariable String codigo) {
        return pdf(entradas.pdfPases(codigo), "entradas-" + codigo + ".pdf");
    }

    @GetMapping(value = "/compra/{codigo}/pase/{numero}.png", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize(Roles.VALIDA_ENTRADAS + " or " + Roles.VENDE_MESAS)
    public ResponseEntity<byte[]> pase(@PathVariable String codigo, @PathVariable int numero) {
        byte[] png = entradas.pngPase(codigo, numero);
        return png == null ? ResponseEntity.notFound().build() : ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    @PostMapping("/{id}/whatsapp")
    @PreAuthorize(Roles.VALIDA_ENTRADAS + " or " + Roles.VENDE_MESAS)
    public ResponseEntity<Map<String, Object>> whatsapp(@PathVariable Long id, @RequestBody(required = false) Envio envio) {
        var r = envios.enviar(id, envio != null && Boolean.TRUE.equals(envio.alExtra()));
        return ResponseEntity.status(r.ok() ? 200 : 409).body(Map.of("ok", r.ok(), "mensaje", r.mensaje()));
    }

    @GetMapping("/compra/{codigo}/recibo.pdf")
    @PreAuthorize(Roles.VALIDA_ENTRADAS + " or " + Roles.VENDE_MESAS)
    public ResponseEntity<byte[]> recibo(@PathVariable String codigo) {
        return pdf(entradas.recibo(codigo), "recibo-" + codigo + ".pdf");
    }

    private static ResponseEntity<byte[]> pdf(byte[] contenido, String nombre) {
        if (contenido == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombre.replaceAll("[^A-Za-z0-9.-]", "") + "\"")
                .body(contenido);
    }

    private ResponseEntity<Map<String, Object>> responder(EntradaService.Resultado r) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        if (r.datos() != null) cuerpo.putAll(r.datos());
        return ResponseEntity.status(r.ok() ? 200 : 409).body(cuerpo);
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }
}

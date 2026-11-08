package com.usic.uniFex.controller.publico;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.service.EntradaService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/publico/entradas")
@RequiredArgsConstructor
public class EntradaPublicaController {

    private final EntradaService entradas;

    @GetMapping("/{edicion}/disponibles")
    public List<Map<String, Object>> disponibles(@PathVariable Long edicion) {
        return entradas.disponibles(edicion);
    }

    @PostMapping(value = "/{edicion}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> comprar(@PathVariable Long edicion,
                                                       @RequestPart("datos") EntradaService.Compra datos,
                                                       @RequestPart(value = "comprobante", required = false) MultipartFile comprobante,
                                                       HttpServletRequest http) throws IOException {
        String reenviado = http.getHeader("X-Forwarded-For");
        String origen = reenviado != null && !reenviado.isBlank() ? reenviado.split(",")[0].trim() : http.getRemoteAddr();
        EntradaService.Resultado r = entradas.comprarEnLinea(edicion, datos, comprobante, origen);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        if (r.datos() != null) cuerpo.putAll(r.datos());
        return ResponseEntity.status(r.ok() ? 201 : 409).body(cuerpo);
    }

    @GetMapping("/compra/{codigo}")
    public ResponseEntity<Map<String, Object>> consultar(@PathVariable String codigo) {
        Map<String, Object> c = entradas.consultar(codigo);
        return c == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(c);
    }

    @GetMapping("/compra/{codigo}/pases.pdf")
    public ResponseEntity<byte[]> pases(@PathVariable String codigo) {
        return pdf(entradas.pdfPases(codigo), "entradas-" + codigo + ".pdf");
    }

    @GetMapping(value = "/compra/{codigo}/pase/{numero}.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> pase(@PathVariable String codigo, @PathVariable int numero) {
        byte[] png = entradas.pngPase(codigo, numero);
        return png == null ? ResponseEntity.notFound().build() : ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(png);
    }

    @GetMapping("/compra/{codigo}/recibo.pdf")
    public ResponseEntity<byte[]> recibo(@PathVariable String codigo) {
        return pdf(entradas.recibo(codigo), "recibo-" + codigo + ".pdf");
    }

    static ResponseEntity<byte[]> pdf(byte[] contenido, String nombre) {
        if (contenido == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nombre.replaceAll("[^A-Za-z0-9.-]", "") + "\"")
                .body(contenido);
    }
}

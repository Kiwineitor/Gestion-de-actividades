package com.usic.uniFex.controller.credenciales;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.usic.uniFex.model.dto.CredencialDTO;
import com.usic.uniFex.model.dto.PlantillaCredencial;
import com.usic.uniFex.model.service.CredencialCodigoService;
import com.usic.uniFex.model.service.CredencialImagenService;
import com.usic.uniFex.model.service.CredencialPdfService;
import com.usic.uniFex.model.service.CredencialService;
import com.usic.uniFex.model.service.ImagenPlantillaCredencial;
import com.usic.uniFex.model.service.PlantillaCredencialService;
import com.usic.uniFex.model.service.PlantillaCredencialService.Datos;
import com.usic.uniFex.model.service.PlantillaCredencialService.Fila;
import com.usic.uniFex.model.service.PlantillaCredencialService.PlantillaInvalida;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/plantillas-credencial")
@PreAuthorize(Roles.ADMINISTRA)
@RequiredArgsConstructor
public class PlantillaCredencialApiController {

    private final PlantillaCredencialService service;
    private final CredencialPdfService pdfService;
    private final CredencialImagenService imagenService;
    private final CredencialService credencialService;
    private final CredencialCodigoService codigos;
    private final ImagenPlantillaCredencial imagenes;

    @Value("${unifex.publico.base-url:}")
    private String baseUrlPublica;

    public record PeticionVistaPrevia(Datos plantilla, Long responsableId, String formato, Double anchoCm) {
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return service.listar().stream().map(this::conProporcion).toList();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody Datos datos) {
        return ResponseEntity.ok(conProporcion(service.crear(datos, usuarioActual())));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> actualizar(@PathVariable Long id, @RequestBody Datos datos) {
        Fila f = service.actualizar(id, datos, usuarioActual());
        return f == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(conProporcion(f));
    }

    @PostMapping("/{id}/duplicar")
    public ResponseEntity<Map<String, Object>> duplicar(@PathVariable Long id) {
        Fila f = service.duplicar(id, usuarioActual());
        return f == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(conProporcion(f));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminar(@PathVariable Long id) {
        boolean ok = service.eliminar(id, usuarioActual());
        return ok ? ResponseEntity.ok(Map.of("ok", true)) : ResponseEntity.notFound().build();
    }

    @PostMapping(value = "/fondo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> subirFondo(@RequestParam("archivo") MultipartFile archivo)
            throws IOException {
        String imagen = service.subirFondo(archivo);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("imagen", imagen);
        m.put("url", imagen);
        return ResponseEntity.ok(m);
    }

    @GetMapping("/fondo")
    public ResponseEntity<byte[]> fondo(@RequestParam("imagen") String imagen) {
        boolean permitida = imagen.startsWith("static/assets/")
                || imagen.startsWith(ImagenPlantillaCredencial.PREFIJO_SUBIDA + "credenciales/");
        if (!permitida || imagen.contains("..") || !imagenes.existe(imagen)) return ResponseEntity.notFound().build();
        MediaType tipo = imagen.toLowerCase().endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(tipo)
                .header("Cache-Control", "private, max-age=3600")
                .body(imagenes.bytes(imagen));
    }

    @PostMapping("/vista-previa")
    public ResponseEntity<byte[]> vistaPrevia(@RequestBody PeticionVistaPrevia req) {
        if (req == null || req.plantilla() == null) throw new PlantillaInvalida("Falta la plantilla");
        Datos d = req.plantilla();
        PlantillaCredencial base = d.codigo() == null ? null : PlantillaCredencial.de(d.codigo()).orElse(null);
        PlantillaCredencial p = service.aPlantilla(
                base == null ? new Datos("VISTA_PREVIA", d.etiqueta(), d.detalle(), d.imagen(), d.requiereFoto(),
                        d.centrado(), d.mayusculas(), d.fotoCircular(), d.multilinea(), d.colorTexto(),
                        d.tipoTexto(), d.cajas(), d.orden(), d.predeterminada()) : d,
                base);
        CredencialDTO c = req.responsableId() == null ? null
                : credencialService.porResponsable(req.responsableId()).orElse(null);
        if (c == null) c = ejemplo();
        if ("png".equalsIgnoreCase(req.formato())) {
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                    .body(imagenService.generar(c, p, raizPublica()));
        }
        double ancho = req.anchoCm() == null ? CredencialPdfService.ANCHO_CM_POR_DEFECTO : req.anchoCm();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .body(pdfService.generar(List.of(c), p, ancho, raizPublica(), codigos));
    }

    @ExceptionHandler(PlantillaInvalida.class)
    public ResponseEntity<Map<String, Object>> invalida(PlantillaInvalida e) {
        return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", e.getMessage()));
    }

    private Map<String, Object> conProporcion(Fila f) {
        Map<String, Object> m = service.aMapa(f);
        double proporcion;
        try {
            proporcion = pdfService.proporcion(f.plantilla());
        } catch (IllegalStateException e) {
            proporcion = 1.3;
        }
        m.put("proporcion", proporcion);
        return m;
    }

    private static CredencialDTO ejemplo() {
        return new CredencialDTO(null, "FXC-1A-EJEMPLO000", "Nombre Apellido Ejemplo", "1234567",
                null, true, "Empresa de ejemplo", "Venta de productos", "Categoría", null, "12",
                null, true, false, false, null, null, false, null);
    }

    private String raizPublica() {
        if (baseUrlPublica != null && !baseUrlPublica.isBlank()) return baseUrlPublica.trim();
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }
}

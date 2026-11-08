package com.usic.uniFex.controller.edicion;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.service.EntradaDisenoService;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/ediciones/{id}/entrada-diseno")
@RequiredArgsConstructor
public class EntradaDisenoApiController {

    private final EntradaDisenoService disenos;

    public record Cambio(String estilo, Boolean datos) {
    }

    @GetMapping
    @PreAuthorize(Roles.GESTIONA_ACTIVIDADES)
    public Map<String, Object> ver(@PathVariable Long id) {
        return disenos.describir(id);
    }

    @PutMapping
    @PreAuthorize(Roles.GESTIONA_ACTIVIDADES)
    public ResponseEntity<Map<String, Object>> guardar(@PathVariable Long id, @RequestBody Cambio cambio) {
        String error = disenos.guardar(id, cambio == null ? null : cambio.estilo(), cambio == null ? null : cambio.datos());
        return error == null ? ResponseEntity.ok(Map.of("ok", true, "mensaje", "Diseño de la entrada guardado"))
                : ResponseEntity.unprocessableEntity().body(Map.of("ok", false, "mensaje", error));
    }

    @PostMapping(value = "/fondo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.GESTIONA_ACTIVIDADES)
    public ResponseEntity<Map<String, Object>> fondo(@PathVariable Long id, @RequestPart("archivo") MultipartFile archivo) throws IOException {
        String error = disenos.subirFondo(id, archivo);
        return error == null ? ResponseEntity.ok(Map.of("ok", true, "mensaje", "Fondo guardado"))
                : ResponseEntity.unprocessableEntity().body(Map.of("ok", false, "mensaje", error));
    }

    @GetMapping(value = "/vista.png", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize(Roles.GESTIONA_ACTIVIDADES)
    public ResponseEntity<byte[]> vista(@PathVariable Long id, @RequestParam(required = false) String estilo,
                                        @RequestParam(required = false) Boolean datos) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_PNG)
                .body(disenos.vistaPrevia(id, estilo, datos));
    }
}

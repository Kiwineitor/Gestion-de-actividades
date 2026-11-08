package com.usic.uniFex.controller.administracion;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.dto.AnuncioDTO;
import com.usic.uniFex.model.service.AnuncioService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/anuncios")
@RequiredArgsConstructor
public class AnuncioApiController {

    private final AnuncioService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<AnuncioDTO> vigentes() {
        return service.vigentes();
    }

    @GetMapping("/historial")
    @PreAuthorize(Roles.COMUNICA)
    public List<AnuncioDTO> historial() {
        return service.historial();
    }

    @GetMapping("/actividades")
    @PreAuthorize(Roles.COMUNICA)
    public List<Map<String, Object>> actividades() {
        return service.actividades();
    }

    @PostMapping
    @PreAuthorize(Roles.COMUNICA)
    public ResponseEntity<?> publicar(@RequestBody AnuncioService.NuevoAnuncio req) {
        try {
            return ResponseEntity.ok(service.publicar(req, usuarioActual()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.COMUNICA)
    public ResponseEntity<?> editar(@PathVariable Long id, @RequestBody AnuncioService.NuevoAnuncio req) {
        try {
            return ResponseEntity.ok(service.editar(id, req, usuarioActual()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", e.getMessage()));
        }
    }

    @PostMapping(value = "/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.COMUNICA)
    public ResponseEntity<?> subirImagen(@RequestParam("archivo") MultipartFile archivo) throws IOException {
        try {
            String ruta = service.subirImagen(archivo);
            return ResponseEntity.ok(Map.of("ok", true, "imagen", ruta, "url", "/files/" + ruta));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.COMUNICA)
    public ResponseEntity<?> retirar(@PathVariable Long id) {
        return service.retirar(id, usuarioActual())
                ? ResponseEntity.ok(Map.of("ok", true))
                : ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "El anuncio no existe"));
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }
}

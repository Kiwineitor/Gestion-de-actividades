package com.usic.uniFex.controller.sistema;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.service.SistemaService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SistemaApiController {

    private final SistemaService sistema;

    @GetMapping("/api/publico/sistema")
    public Map<String, Object> marca() {
        return sistema.marca();
    }

    @PutMapping("/api/app/sistema")
    @PreAuthorize(Roles.SOLO_SUPER_USUARIO)
    public ResponseEntity<Map<String, Object>> guardar(@RequestBody SistemaService.Datos datos) {
        return responder(sistema.guardar(datos, usuarioActual()));
    }

    @PostMapping(value = "/api/app/sistema/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.SOLO_SUPER_USUARIO)
    public ResponseEntity<Map<String, Object>> subirLogo(@RequestParam("archivo") MultipartFile archivo) throws IOException {
        return responder(sistema.subirLogo(archivo, usuarioActual()));
    }

    @DeleteMapping("/api/app/sistema/logo")
    @PreAuthorize(Roles.SOLO_SUPER_USUARIO)
    public ResponseEntity<Map<String, Object>> quitarLogo() {
        return responder(sistema.quitarLogo(usuarioActual()));
    }

    private static ResponseEntity<Map<String, Object>> responder(SistemaService.Resultado r) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        if (r.marca() != null) cuerpo.put("marca", r.marca());
        return ResponseEntity.status(r.ok() ? 200 : 409).body(cuerpo);
    }

    private static Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }
}

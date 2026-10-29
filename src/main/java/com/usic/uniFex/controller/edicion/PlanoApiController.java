package com.usic.uniFex.controller.edicion;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.dto.PlanoDTO;
import com.usic.uniFex.model.service.PlanoService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/plano")
@RequiredArgsConstructor
public class PlanoApiController {

    private final PlanoService planoService;

    @GetMapping
    public PlanoDTO activo() {
        return planoService.activo();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.EDITA_PLANO)
    public ResponseEntity<Map<String, Object>> reemplazar(@RequestPart("archivo") MultipartFile archivo) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long usuarioId = (auth != null && auth.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }

        PlanoService.Resultado r = planoService.reemplazar(archivo, usuarioId);

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("plano", r.plano());
        return r.ok() ? ResponseEntity.ok(cuerpo) : ResponseEntity.badRequest().body(cuerpo);
    }
}

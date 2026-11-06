package com.usic.uniFex.controller.edicion;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
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
    @PreAuthorize("#edicion == null or (" + Roles.EDITA_PLANO_O_PANTALLA + ")")
    public PlanoDTO activo(@RequestParam(required = false) Long edicion) {
        return planoService.deEdicion(edicion);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.EDITA_PLANO_O_PANTALLA)
    public ResponseEntity<Map<String, Object>> reemplazar(@RequestPart("archivo") MultipartFile archivo, @RequestParam(required = false) Long edicion) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long usuarioId = (auth != null && auth.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }

        return responder(planoService.reemplazar(archivo, usuarioId, edicion));
    }

    public record Forma(Integer ancho, Integer alto, Boolean conservar) {
    }

    @PatchMapping("/forma")
    @PreAuthorize(Roles.EDITA_PLANO_O_PANTALLA)
    public ResponseEntity<Map<String, Object>> cambiarForma(@RequestBody Forma req, @RequestParam(required = false) Long edicion) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long usuarioId = (auth != null && auth.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
        return responder(planoService.cambiarForma(req.ancho(), req.alto(), !Boolean.FALSE.equals(req.conservar()), usuarioId, edicion));
    }

    @DeleteMapping
    @PreAuthorize(Roles.EDITA_PLANO_O_PANTALLA)
    public ResponseEntity<Map<String, Object>> quitarFondo(@RequestParam(required = false) Long edicion) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long usuarioId = (auth != null && auth.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
        return responder(planoService.quitarFondo(usuarioId, edicion));
    }

    private ResponseEntity<Map<String, Object>> responder(PlanoService.Resultado r) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("plano", r.plano());
        return r.ok() ? ResponseEntity.ok(cuerpo) : ResponseEntity.badRequest().body(cuerpo);
    }
}

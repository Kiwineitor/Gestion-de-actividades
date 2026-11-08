package com.usic.uniFex.controller.inscripcion;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.service.LlegadaService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/llegadas")
@RequiredArgsConstructor
public class LlegadaApiController {

    private final LlegadaService llegadas;

    public record Confirmacion(Integer personas, String nota) {
    }

    @GetMapping
    @PreAuthorize(Roles.INGRESO_ENTRADAS)
    public Map<String, Object> listar(@RequestParam(required = false) Long edicion,
                                      @RequestParam(required = false) String q) {
        return llegadas.listar(edicion, q);
    }

    @PostMapping("/{inscripcionId}")
    @PreAuthorize(Roles.INGRESO_ENTRADAS)
    public ResponseEntity<Map<String, Object>> confirmar(@PathVariable Long inscripcionId,
                                                         @RequestBody(required = false) Confirmacion datos) {
        LlegadaService.Resultado r = llegadas.confirmar(inscripcionId,
                datos == null ? null : datos.personas(), datos == null ? null : datos.nota(), usuarioActual());
        return respuesta(r);
    }

    @DeleteMapping("/{inscripcionId}")
    @PreAuthorize(Roles.INGRESO_ENTRADAS)
    public ResponseEntity<Map<String, Object>> deshacer(@PathVariable Long inscripcionId) {
        return respuesta(llegadas.deshacer(inscripcionId));
    }

    private static ResponseEntity<Map<String, Object>> respuesta(LlegadaService.Resultado r) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("repetido", r.repetido());
        return r.ok() || r.repetido() ? ResponseEntity.ok(cuerpo) : ResponseEntity.unprocessableEntity().body(cuerpo);
    }

    private static Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof JwtUser u ? u.id() : null;
    }
}

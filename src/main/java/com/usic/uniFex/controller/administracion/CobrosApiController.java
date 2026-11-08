package com.usic.uniFex.controller.administracion;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.service.CobrosService;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/cobros")
@RequiredArgsConstructor
@PreAuthorize(Roles.VE_CONTROL_VENTAS + " or " + Roles.VALIDA_ENTRADAS)
public class CobrosApiController {

    private final CobrosService cobros;

    @GetMapping("/{edicion}/resumen")
    public ResponseEntity<Map<String, Object>> resumen(@PathVariable Long edicion) {
        Map<String, Object> r = cobros.resumen(edicion);
        return r == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(r);
    }

    @GetMapping("/{edicion}/ventas")
    public List<Map<String, Object>> ventas(@PathVariable Long edicion) {
        return cobros.ventas(edicion);
    }
}

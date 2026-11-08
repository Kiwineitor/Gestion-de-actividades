package com.usic.uniFex.controller.usuario;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.service.HistorialService;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/historial")
@RequiredArgsConstructor
@PreAuthorize(Roles.VE_HISTORIAL)
public class HistorialApiController {

    private final HistorialService historial;

    @GetMapping
    public List<Map<String, Object>> listar(@RequestParam(required = false) Long usuario,
                                            @RequestParam(required = false) String modulo,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) Boolean errores,
                                            @RequestParam(required = false) Long antesDe,
                                            @RequestParam(required = false) Integer limite) {
        return historial.listar(new HistorialService.Filtros(usuario, modulo, desde, hasta, q, errores, antesDe, limite));
    }

    @GetMapping("/opciones")
    public Map<String, Object> opciones() {
        return historial.opciones();
    }
}

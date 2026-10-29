package com.usic.uniFex.controller.inscripcion;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.IService.ITipoEntidadService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/catalogos")
@RequiredArgsConstructor
public class CatalogosApiController {

    private static final String ANULADO = "X";

    private final ITipoEntidadService tipoEntidadService;

    @GetMapping("/tipos-entidad")
    public List<Map<String, Object>> tiposEntidad() {
        return tipoEntidadService.findAll().stream()
                .filter(t -> !ANULADO.equals(t.getEstado()))
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .map(t -> Map.<String, Object>of("id", t.getId(), "nombre", t.getNombre()))
                .toList();
    }
}

package com.usic.uniFex.controller.inscripcion;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.service.InscripcionGeneralService;
import com.usic.uniFex.security.AccesoPantallas;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/inscripciones/general")
@RequiredArgsConstructor
public class InscripcionGeneralApiController {

    private static final String VE_LISTADO = Roles.VE_INSCRIPCIONES_O_PANTALLA
            + " or " + Roles.VE_CONTROL_VENTAS
            + " or @acceso.puede('reportes') or @acceso.puede('direccion')";

    private final InscripcionGeneralService servicio;

    @GetMapping
    @PreAuthorize(VE_LISTADO)
    public List<Map<String, Object>> listar(@RequestParam(required = false) String actividad,
                                            @RequestParam(required = false) String categoria,
                                            @RequestParam(required = false) Long vendedor,
                                            @RequestParam(required = false) String estado,
                                            @RequestParam(required = false) String q) {
        return servicio.listar(new InscripcionGeneralService.Filtros(actividad, categoria, vendedor, estado, q, soloDe()));
    }

    @GetMapping("/opciones")
    @PreAuthorize(VE_LISTADO)
    public Map<String, Object> opciones(@RequestParam(required = false) String actividad) {
        return servicio.opciones(actividad, soloDe());
    }

    private static Long soloDe() {
        return AccesoPantallas.soloDe(Roles.AUTORIDADES_VEN_TODAS_LAS_INSCRIPCIONES);
    }
}

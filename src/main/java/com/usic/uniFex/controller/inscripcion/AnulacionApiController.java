package com.usic.uniFex.controller.inscripcion;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.service.AnulacionService;
import com.usic.uniFex.security.AccesoPantallas;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/anulaciones")
@RequiredArgsConstructor
public class AnulacionApiController {

    private final AnulacionService anulaciones;

    @GetMapping
    @PreAuthorize(Roles.VE_INSCRIPCIONES_O_PANTALLA)
    public List<Map<String, Object>> listar(@RequestParam(required = false) Long edicion) {
        return anulaciones.listar(edicion, AccesoPantallas.soloDe(Roles.AUTORIDADES_VEN_TODAS_LAS_INSCRIPCIONES));
    }
}

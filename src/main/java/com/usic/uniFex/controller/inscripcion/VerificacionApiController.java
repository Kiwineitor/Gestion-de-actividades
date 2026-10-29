package com.usic.uniFex.controller.inscripcion;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.service.NotaVentaCodigoService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/publico/notas")
@RequiredArgsConstructor
public class VerificacionApiController {

    private final NotaVentaCodigoService codigoService;

    @GetMapping("/{codigo}")
    public NotaVentaCodigoService.Verificacion verificar(@PathVariable String codigo) {
        return codigoService.verificar(codigo);
    }
}

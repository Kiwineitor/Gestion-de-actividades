package com.usic.uniFex.controller.persona;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dto.AreaDTO;
import com.usic.uniFex.model.dto.CarreraDTO;
import com.usic.uniFex.model.service.CatalogoAcademicoService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class CatalogoAcademicoApiController {

    private final CatalogoAcademicoService catalogo;

    @GetMapping("/areas")
    public List<AreaDTO> areas() {
        return catalogo.areasConCarreras();
    }

    @GetMapping("/carreras")
    public List<CarreraDTO> carreras() {
        return catalogo.carreras();
    }
}

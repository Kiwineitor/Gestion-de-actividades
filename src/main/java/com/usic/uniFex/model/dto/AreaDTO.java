package com.usic.uniFex.model.dto;

import java.util.List;

import com.usic.uniFex.model.entity.Area;

public record AreaDTO(Long id, String sigla, String nombre, List<CarreraDTO> carreras) {

    public static AreaDTO de(Area a, List<CarreraDTO> carreras) {
        return new AreaDTO(a.getId(), a.getSigla(), a.getNombre(), carreras);
    }
}

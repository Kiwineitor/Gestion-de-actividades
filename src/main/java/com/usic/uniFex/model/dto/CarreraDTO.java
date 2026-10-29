package com.usic.uniFex.model.dto;

import java.util.List;

import com.usic.uniFex.model.entity.Area;
import com.usic.uniFex.model.entity.Carrera;

public record CarreraDTO(Long id, String nombre, Long areaId, String areaSigla, String areaNombre) {

    public static CarreraDTO de(Carrera c) {
        if (c == null) return null;
        Area a = c.getArea();
        return new CarreraDTO(c.getId(), c.getNombre(),
                a != null ? a.getId() : null,
                a != null ? a.getSigla() : null,
                a != null ? a.getNombre() : null);
    }

    public static List<CarreraDTO> de(List<Carrera> carreras) {
        return carreras.stream().map(CarreraDTO::de).toList();
    }
}

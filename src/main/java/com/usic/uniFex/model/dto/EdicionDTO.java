package com.usic.uniFex.model.dto;

import com.usic.uniFex.model.entity.Edicion;

public record EdicionDTO(
        Long id,
        String nombre,
        Integer anio,
        boolean activa) {

    public static EdicionDTO de(Edicion e) {
        return new EdicionDTO(e.getId(), e.getNombre(), e.getAnio(), Boolean.TRUE.equals(e.getActiva()));
    }
}

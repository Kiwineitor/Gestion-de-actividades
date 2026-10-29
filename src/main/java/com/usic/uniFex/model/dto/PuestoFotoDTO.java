package com.usic.uniFex.model.dto;

import com.usic.uniFex.model.entity.PuestoFoto;

public record PuestoFotoDTO(Long id, String url, String descripcion, Integer orden) {

    public static PuestoFotoDTO de(PuestoFoto f) {
        return new PuestoFotoDTO(
                f.getId(),
                "/files/" + f.getRuta(),
                f.getDescripcion(),
                f.getOrden());
    }
}

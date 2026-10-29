package com.usic.uniFex.model.dto;

import java.time.LocalDateTime;

public record OcupacionPuestoDTO(
        Long puestoId,
        Long vendedorId,
        String vendedor,
        String celular,
        String estado,
        LocalDateTime desde) {
}

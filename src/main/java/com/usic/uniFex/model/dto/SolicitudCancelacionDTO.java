package com.usic.uniFex.model.dto;

import java.time.LocalDateTime;

public record SolicitudCancelacionDTO(
        Long id,
        Long inscripcionId,
        String entidad,
        String vendedor,
        String motivo,
        String estado,
        String respuesta,
        LocalDateTime fechaSolicitud,
        LocalDateTime fechaResolucion,
        String resueltoPor) {
}

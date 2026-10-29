package com.usic.uniFex.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PromotoresListadoDTO {
    private Long id;
    private Long entidadId;
    private String entidadNombre;
    private String persona;
    private String ci;
    private String foto;
}

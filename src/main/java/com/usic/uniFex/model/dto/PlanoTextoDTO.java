package com.usic.uniFex.model.dto;

import com.usic.uniFex.model.entity.PlanoTexto;

public record PlanoTextoDTO(
        Long id,
        String contenido,
        Double mapaX,
        Double mapaY,
        Double tamano,
        String color,
        String colorBorde,
        Double grosorBorde,
        Double rotacion,
        boolean activo) {

    public static PlanoTextoDTO de(PlanoTexto t) {
        return new PlanoTextoDTO(
                t.getId(),
                t.getContenido(),
                t.getMapaX(),
                t.getMapaY(),
                t.getTamano(),
                t.getColor(),
                t.getColorBorde(),
                t.getGrosorBorde(),
                t.getRotacion(),
                !PlanoTexto.REGISTRO_ANULADO.equals(t.getEstado()));
    }

    public static PlanoTextoDTO baja(Long id) {
        return new PlanoTextoDTO(id, null, null, null, null, null, null, null, null, false);
    }
}

package com.usic.uniFex.model.dto;

import com.usic.uniFex.model.entity.Edicion;

public record PlanoDTO(
        String url,
        Integer ancho,
        Integer alto,
        int version,
        boolean propio) {

    private static final String RESPALDO_URL = "/mapa.png";
    private static final int RESPALDO_ANCHO = 1836;
    private static final int RESPALDO_ALTO = 2376;

    public static PlanoDTO respaldo() {
        return new PlanoDTO(RESPALDO_URL, RESPALDO_ANCHO, RESPALDO_ALTO, 0, false);
    }

    public static PlanoDTO de(Edicion e) {
        if (e == null || e.getPlanoArchivo() == null
                || e.getPlanoAncho() == null || e.getPlanoAlto() == null) {
            return respaldo();
        }
        int version = e.getPlanoVersion() == null ? 0 : e.getPlanoVersion();
        return new PlanoDTO(
                "/files/" + e.getPlanoArchivo() + "?v=" + version,
                e.getPlanoAncho(), e.getPlanoAlto(), version, true);
    }
}

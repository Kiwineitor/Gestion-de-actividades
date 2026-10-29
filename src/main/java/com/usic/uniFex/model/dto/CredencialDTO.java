package com.usic.uniFex.model.dto;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public record CredencialDTO(
        Long responsableId,
        String codigo,
        String nombre,
        String ci,
        String fotoUrl,
        boolean esTitular,
        String entidad,
        String rubro,
        String categoria,
        Long categoriaId,
        String casetas,
        Long inscripcionId,
        boolean conComprobante,
        boolean conFoto,
        boolean esExtra,
        java.math.BigDecimal montoExtra,
        String comprobanteExtraUrl,
        boolean sinCosto,
        String cargo) {

    public static boolean requiereFoto(String plantilla) {
        return PlantillaCredencial.oPorDefecto(plantilla).requiereFoto();
    }

    public boolean requiereComprobante() {
        if (!sinCosto) return true;
        return esExtra && comprobanteExtraUrl == null;
    }

    public boolean apto(String plantilla) {
        if (requiereComprobante() && !conComprobante) return false;
        return !requiereFoto(plantilla) || conFoto;
    }

    public List<String> faltantes(String plantilla) {
        return Stream.of(
                        (requiereComprobante() && !conComprobante) ? "sin comprobante" : null,
                        (requiereFoto(plantilla) && !conFoto) ? "sin foto" : null)
                .filter(Objects::nonNull)
                .toList();
    }
}

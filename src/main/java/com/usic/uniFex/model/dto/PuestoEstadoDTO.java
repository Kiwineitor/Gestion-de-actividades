package com.usic.uniFex.model.dto;

import java.time.LocalDateTime;

import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.Puesto;

public record PuestoEstadoDTO(
        Long id,
        String codigo,
        String tamano,
        Long categoriaId,
        String categoria,
        String color,
        String forma,
        String estado,
        LocalDateTime reservaExpira,
        Long reservadoPor,
        Double mapaX,
        Double mapaY,
        Double tamanoMapa,
        Double mapaEscala,
        Integer mapaRotacion,
        java.math.BigDecimal precio,
        boolean precioPropio,
        String referencia,
        Double mapaProporcion,
        String observacion,
        boolean activo) {

    private static final String ANULADO = "X";

    private static final double TAMANO_POR_DEFECTO = 0.012;

    private static java.math.BigDecimal precioDe(Puesto p, Categoria c) {
        if (p.getPrecio() != null) return p.getPrecio();
        return (c != null && c.getPrecioBase() != null) ? c.getPrecioBase() : java.math.BigDecimal.ZERO;
    }

    public static PuestoEstadoDTO de(Puesto p) {
        Categoria c = p.getCategoria();
        return new PuestoEstadoDTO(
                p.getId(),
                p.getCodigo(),
                p.getTamano(),
                c != null ? c.getId() : null,
                c != null ? c.getNombre() : null,
                c != null ? c.getColor() : null,
                c != null ? c.getForma() : null,
                p.getEstadoPuesto(),
                p.getReservaExpira(),
                p.getReservadoPorIdUsuario(),
                p.getMapaX(),
                p.getMapaY(),
                (c != null && c.getTamanoMapa() != null) ? c.getTamanoMapa() : TAMANO_POR_DEFECTO,
                p.getMapaEscala() != null ? p.getMapaEscala() : 1.0,
                p.getMapaRotacion() != null ? p.getMapaRotacion() : 0,
                precioDe(p, c),
                p.getPrecio() != null,
                p.getReferencia(),
                (c != null && c.getMapaProporcion() != null) ? c.getMapaProporcion() : 1.0,
                p.getObservacion(),
                !ANULADO.equals(p.getEstado()));
    }
}

package com.usic.uniFex.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AnalisisDTO {

    private AnalisisDTO() {
    }

    public record Ocupacion(
            String categoria,
            int vendidas,
            int enTramite,
            int libres,
            int bloqueadas,
            int total,
            BigDecimal porcentajeVendido,
            BigDecimal bsVendido,
            BigDecimal bsPorVender) {
    }

    public record Vendedor(
            Long usuarioId,
            String vendedor,
            String area,
            String carrera,
            int ventas,
            int casetas,
            BigDecimal totalBs,
            BigDecimal ticketMedio) {
    }

    public record Facultad(
            String sigla,
            String nombre,
            int vendedores,
            int ventas,
            int casetas,
            BigDecimal totalBs,
            BigDecimal porcentaje) {
    }

    public record VendidoCategoria(
            String categoria,
            int vendidas,
            int total,
            BigDecimal porcentajeVendido,
            BigDecimal totalBs,
            BigDecimal porcentajeDelDinero) {
    }

    public record Cobro(
            String concepto,
            int ventas,
            int casetas,
            BigDecimal totalBs) {
    }

    public record Pendiente(
            Long inscripcionId,
            String entidad,
            String vendedor,
            boolean contado,
            BigDecimal totalBs,
            LocalDate fecha,
            int dias) {
    }

    public record Dia(
            LocalDate fecha,
            int ventas,
            int casetas,
            BigDecimal totalBs,
            BigDecimal acumuladoBs,
            int acumuladoCasetas) {
    }

    public record Cobros(List<Cobro> cortes, List<Pendiente> pendientes,
                         BigDecimal totalCobrado, BigDecimal totalPendiente) {
    }
}

package com.usic.uniFex.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

public final class ControlVentasDTO {

    private ControlVentasDTO() {
    }

    public static final String PAGADO = "PAGADO";
    public static final String PARCIAL = "PARCIAL";
    public static final String PENDIENTE = "PENDIENTE";
    public static final String SIN_COSTO = "SIN_COSTO";

    @Getter
    @Setter
    public static class Venta {
        private Long inscripcionId;
        private String codigoVenta;
        private LocalDateTime fecha;
        private String estadoInscripcion;

        private Long vendedorId;
        private String vendedor;
        private String vendedorUsuario;

        private String codigoExpositor;
        private String baseCodigo;
        private int comprasExpositor;
        private Long entidadId;
        private String entidad;
        private String rubro;
        private String tipoEntidad;
        private String nit;
        private String representante;
        private String ciRepresentante;
        private String celularRepresentante;
        private String titular;
        private String ciTitular;
        private String celularTitular;
        private String responsables;
        private String nombresExtras;

        private String categorias;
        private String subcategorias;
        private String puestos;
        private String ubicacion;
        private int cantidadPuestos;
        private BigDecimal precioUnitario;
        private String preciosUnitarios;
        private BigDecimal precioLista;
        private BigDecimal descuento;
        private int puestosExentos;
        private String motivoExencion;
        private BigDecimal importePuestos;

        private int extras;
        private BigDecimal precioExtra;
        private BigDecimal importeExtras;
        private int extrasSinComprobante;

        private BigDecimal totalVendido;
        private BigDecimal recaudadoPuestos;
        private BigDecimal recaudadoExtras;
        private BigDecimal totalRecaudado;
        private BigDecimal porRendir;
        private String estadoPago;
        private String formaPago;
        private String entidadBancaria;
        private String numComprobante;
        private boolean conComprobante;
        private String comprobanteUrl;

        private int carpasCorresponden;
        private int credencialesIncluidas;
        private int credencialesTotal;
        private int responsablesRegistrados;
        private int credencialesEmitidas;
        private LocalDate fechaEmision;

        public int getCredencialesPendientes() {
            return Math.max(0, credencialesTotal - credencialesEmitidas);
        }
    }

    public record Puesto(
            Long inscripcionId, String codigoVenta, LocalDateTime fecha, String vendedor,
            String codigoExpositor, String entidad, Long puestoId, String codigo, String categoria,
            String subcategoria, BigDecimal precioLista, BigDecimal precio, BigDecimal descuento,
            boolean exento, String motivoExencion) {
    }

    public record Responsable(
            Long inscripcionId, String codigoVenta, String entidad, String vendedor,
            Long responsableId, String tipo, String nombre, String ci, String celular,
            BigDecimal montoExtra, boolean conComprobante, String comprobanteUrl,
            LocalDateTime registrado, boolean credencialEmitida, LocalDateTime emitidaEn,
            String emitidaPor, int vecesGenerada) {
    }

    public record Vendedor(
            Long usuarioId, String vendedor, String usuario, int operaciones, int expositores,
            int puestos, int extras, BigDecimal importePuestos, BigDecimal importeExtras,
            BigDecimal totalVendido, BigDecimal recaudadoPuestos, BigDecimal recaudadoExtras,
            BigDecimal totalRecaudado, BigDecimal porRendir, int ventasPorRendir,
            BigDecimal porcentajeVendido, int credencialesTotal, int credencialesEmitidas) {
    }

    public record Categoria(
            String categoria, int puestosVendidos, int puestosTotales, BigDecimal porcentajePuestos,
            BigDecimal porcentajeOcupacion, BigDecimal vendidoBs, BigDecimal porcentajeIngresos,
            BigDecimal recaudadoBs, int exentos) {
    }

    public record Dia(LocalDate fecha, int operaciones, int puestos, BigDecimal totalBs,
                      BigDecimal acumuladoBs) {
    }

    public record Resumen(
            int operaciones, int expositores, int vendedores, int puestos, int puestosExentos,
            int extras, BigDecimal importePuestos, BigDecimal importeExtras,
            BigDecimal totalVendido, BigDecimal recaudadoPuestos, BigDecimal recaudadoExtras,
            BigDecimal totalRecaudado, BigDecimal porRendir,
            int ventasConComprobante, int ventasParciales, int ventasPorRendir, int ventasSinCosto,
            int carpasCorresponden, int credencialesIncluidas, int credencialesExtras,
            int credencialesTotal, int responsablesRegistrados, int credencialesEmitidas) {
    }

    public record Tablero(Resumen resumen, List<Vendedor> vendedores, List<Categoria> categorias,
                          List<Dia> avance) {
    }

    public record Ficha(Venta venta, List<Puesto> puestos, List<Responsable> responsables) {
    }

    public record Opcion(Long id, String nombre) {
    }
}

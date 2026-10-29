package com.usic.uniFex.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record InscripcionDetalleDTO(
    Long id,
    String entidad,
    String nit,
    String tipoEntidad,
    String representanteLegal,
    String ciRepresentante,
    String descripcion,
    String objeto,
    LocalDate fechaInicio,
    LocalDate fechaFin,
    String promotor,
    List<ResponsableDetalle> responsables,
    List<PuestoDetalle> puestos,
    BigDecimal total,
    LocalDateTime fechaCompra,
    boolean pagoContado,
    String entidadBancaria,
    String numComprobante,
    String imgComprobante,
    String inscripcionEstado,
    String edicion,
    String motivoCancelacion,
    LocalDateTime fechaCancelacion,
    String canceladaPor,
    String origenCancelacion,
    List<AuditoriaDetalle> auditoria
) {

    public record ResponsableDetalle(String nombreCompleto, String ci, String correo,
                                     String celular, boolean esTitular,
                                     String fotoUrl,
                                     boolean esExtra,
                                     BigDecimal montoExtra,
                                     String comprobanteExtraUrl) {
    }

    public record PuestoDetalle(String codigo, String categoria, BigDecimal costo) {
    }

    public record AuditoriaDetalle(String accion, String detalle, String usuarioNombre,
                                   String origen, LocalDateTime fecha) {
    }
}

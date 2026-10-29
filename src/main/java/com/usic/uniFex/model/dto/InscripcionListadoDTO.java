package com.usic.uniFex.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InscripcionListadoDTO(
    Long id,
    String promotor,
    String entidad,
    String tipoEntidad,
    String nit,
    String nroComprobante,
    Integer cantidadPuestos,
    List<String> categorias,
    BigDecimal total,
    LocalDateTime fecha,
    String estado,
    String imgComprobante,
        boolean pagoContado,
    List<String> codigosPuestos,
    String motivoCancelacion,
    LocalDateTime fechaCancelacion,
    String canceladaPor,
    String origenCancelacion
) {
    public String getNroComprobante() { return nroComprobante; }
    public String getPromotor() { return promotor; }
}

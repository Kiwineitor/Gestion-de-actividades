package com.usic.uniFex.model.dto;

public interface CredencialFilaView {
    Long getResponsableId();
    String getNombre();
    String getPaterno();
    String getMaterno();
    String getCi();
    String getFoto();
    Boolean getEsTitular();
    Boolean getEsExtra();
    java.math.BigDecimal getMontoExtra();
    String getComprobanteExtra();
    String getEntidad();
    String getRubro();
    Long getInscripcionId();
    Boolean getPagoContado();
    String getComprobante();
    java.math.BigDecimal getTotalVenta();
    String getCategorias();
    String getCasetas();
    Long getCategoriaId();
    String getCargo();
}

package com.usic.uniFex.model.dto;

import com.usic.uniFex.model.entity.InstanciaWhatsApp;

public record InstanciaWhatsAppDTO(Long id, String nombre, String urlApi, String instancia, boolean activa) {
    public static InstanciaWhatsAppDTO de(InstanciaWhatsApp entidad) {
        return new InstanciaWhatsAppDTO(entidad.getId(), entidad.getNombre(), entidad.getUrlApi(),
                entidad.getInstancia(), entidad.isActiva());
    }
}

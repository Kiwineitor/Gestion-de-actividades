package com.usic.uniFex.model.dto;

import com.usic.uniFex.model.entity.PersonalApoyo;
import com.usic.uniFex.model.service.ApoyoCodigoService;

public record ApoyoCredencialDTO(
        Long id,
        String codigo,
        String nombreCompleto,
        String ci,
        Long idDependencia,
        String dependenciaNombre,
        String rol,
        String descripcionTarea,
        String fotoUrl) {

    public static ApoyoCredencialDTO de(PersonalApoyo p, ApoyoCodigoService codigos) {
        return new ApoyoCredencialDTO(
                p.getId(),
                codigos.codigoDe(p.getId()),
                p.getNombreCompleto(),
                p.getCi(),
                p.getDependencia() != null ? p.getDependencia().getId() : null,
                p.getDependencia() != null ? p.getDependencia().getNombre() : null,
                p.getRol(),
                p.getDescripcionTarea(),
                PersonalApoyoDTO.fotoUrl(p.getFoto()));
    }
}

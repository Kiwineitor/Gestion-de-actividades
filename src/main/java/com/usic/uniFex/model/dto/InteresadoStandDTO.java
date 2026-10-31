package com.usic.uniFex.model.dto;

import java.time.LocalDateTime;

import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.InteresadoStand;

public record InteresadoStandDTO(
        Long id,
        String nombreCompleto,
        String celular,
        String empresa,
        String rubro,
        Long categoriaId,
        String categoriaNombre,
        String categoriaColor,
        String estado,
        LocalDateTime fechaRegistro,
        Long puestoId,
        String puestoCodigo,
        String modoPago,
        String comprobanteUrl,
        String ci,
        Long inscripcionId,
        Integer personasPorPuesto) {

    public static InteresadoStandDTO de(InteresadoStand i) {
        Categoria c = i.getCategoria();
        return new InteresadoStandDTO(
                i.getId(), i.getNombreCompleto(), i.getCelular(), i.getEmpresa(), i.getRubro(),
                c != null ? c.getId() : null,
                c != null ? c.getNombre() : null,
                c != null ? c.getColor() : null,
                i.getEstado(), i.getFechaRegistro(),
                i.getPuesto() != null ? i.getPuesto().getId() : null,
                i.getPuesto() != null ? i.getPuesto().getCodigo() : null,
                i.getModoPago(),
                i.getComprobanteArchivo() != null ? "/files/" + i.getComprobanteArchivo() : null,
                i.getCi(), i.getInscripcionId(),
                c != null ? c.personasIncluidas() : 2);
    }
}

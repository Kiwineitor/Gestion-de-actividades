package com.usic.uniFex.model.dto;

import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Responsable;

public record ResponsableFotoDTO(
        Long id,
        String nombre,
        String paterno,
        String materno,
        String ci,
        String celular,
        boolean esTitular,
        String fotoUrl,
        boolean tieneFoto,
        boolean esExtra,
        java.math.BigDecimal montoExtra,
        String comprobanteExtraUrl) {

    public static ResponsableFotoDTO de(Responsable r) {
        Persona p = r.getPersona();
        String foto = p != null ? p.getFoto() : null;
        boolean hay = foto != null && !foto.isBlank();
        String nombre = p == null ? "" : java.util.stream.Stream
                .of(p.getNombre(), p.getPaterno(), p.getMaterno())
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("");
        return new ResponsableFotoDTO(
                r.getId(),
                nombre,
                p != null ? p.getPaterno() : null,
                p != null ? p.getMaterno() : null,
                p != null ? p.getCi() : null,
                p != null ? p.getCelular() : null,
                r.isEsTitular(),
                hay ? "/files/" + foto : null,
                hay,
                r.isEsExtra(),
                r.getMontoExtra(),
                (r.getComprobanteExtra() != null && !r.getComprobanteExtra().isBlank())
                        ? "/files/" + r.getComprobanteExtra() : null);
    }
}

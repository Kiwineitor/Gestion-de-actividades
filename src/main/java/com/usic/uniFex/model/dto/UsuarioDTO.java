package com.usic.uniFex.model.dto;

import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Usuario;

public record UsuarioDTO(
        Long id,
        String username,
        Long personaId,
        String persona,
        Long rolId,
        String rol,
        String estado) {

    public static UsuarioDTO de(Usuario u) {
        Persona p = u.getPersona();
        return new UsuarioDTO(
                u.getId(),
                u.getUsername(),
                p != null ? p.getId() : null,
                nombreCompleto(p),
                u.getRol() != null ? u.getRol().getId() : null,
                u.getRol() != null ? u.getRol().getNombre() : null,
                u.getEstado());
    }

    private static String nombreCompleto(Persona p) {
        if (p == null) return null;
        return java.util.stream.Stream.of(p.getNombre(), p.getPaterno(), p.getMaterno())
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + " " + b)
                .orElse("(sin nombre)");
    }
}

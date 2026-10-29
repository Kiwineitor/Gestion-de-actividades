package com.usic.uniFex.model.dto;

import com.usic.uniFex.model.entity.Rol;
import com.usic.uniFex.security.RolesSistema;

public record RolDTO(
        Long id,
        String nombre,
        String descripcion,
        String autoridad,
        boolean sistema,
        long usuarios,
        String estado) {

    public static RolDTO de(Rol r, long usuarios) {
        return new RolDTO(
                r.getId(),
                r.getNombre(),
                r.getDescripcion(),
                autoridadDe(r.getNombre()),
                RolesSistema.esDelSistema(r.getNombre()),
                usuarios,
                r.getEstado());
    }

    private static String autoridadDe(String nombre) {
        return nombre == null ? "" : "ROLE_" + nombre.toUpperCase().replace(' ', '_');
    }
}

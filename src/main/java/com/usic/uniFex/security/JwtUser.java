package com.usic.uniFex.security;

public record JwtUser(Long id, String username, String rol) {

    public String rolNormalizado() {
        return (rol == null) ? "" : rol.toUpperCase().replace(' ', '_');
    }
}

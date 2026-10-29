package com.usic.uniFex.security;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.usic.uniFex.model.service.PermisoPantallaService;

import lombok.RequiredArgsConstructor;

@Component("acceso")
@RequiredArgsConstructor
public class AccesoPantallas {

    private final PermisoPantallaService permisos;

    public boolean puede(String pantalla) {
        JwtUser u = actual();
        if (u == null || pantalla == null) return false;
        if (PermisoPantallaService.loVeTodo(u.rol())) return true;
        return permisos.pantallasDe(u.id(), u.rol()).contains(pantalla);
    }

    public static Long soloDe(List<String> autoridadesQueVenTodo) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return -1L;
        boolean veTodo = a.getAuthorities().stream()
                .anyMatch(g -> autoridadesQueVenTodo.contains(g.getAuthority()));
        if (veTodo) return null;
        return a.getPrincipal() instanceof JwtUser u ? u.id() : -1L;
    }

    private static JwtUser actual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof JwtUser u ? u : null;
    }
}

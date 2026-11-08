package com.usic.uniFex.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AccesoSeguro acceso;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                JwtService.Datos datos = jwtService.leer(header.substring(7));
                JwtUser user = datos.usuario();
                if (acceso.sesionValida(user.id(), datos.version())) {
                    if (!datos.pendientes().isEmpty() && request.getRequestURI().startsWith(request.getContextPath() + "/api/app/")) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"ok\":false,\"codigo\":\"PRIMER_INGRESO\",\"mensaje\":\"Antes de continuar acepta los términos y cambia tu contraseña.\"}");
                        return;
                    }
                    var auth = new UsernamePasswordAuthenticationToken(
                            user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.rolNormalizado())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    org.slf4j.MDC.put("usuario", user.username());
                    org.slf4j.MDC.put("usuarioId", String.valueOf(user.id()));
                } else {
                    SecurityContextHolder.clearContext();
                }
            } catch (Exception e) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}

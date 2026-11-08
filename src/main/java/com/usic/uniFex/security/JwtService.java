package com.usic.uniFex.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.usic.uniFex.model.entity.Usuario;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${unifex.jwt.secret}") String secret,
                      @Value("${unifex.jwt.expiration-ms:86400000}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public record Datos(JwtUser usuario, int version, List<String> pendientes) {
    }

    public String generar(Usuario u) {
        return generar(u, List.of());
    }

    public String generar(Usuario u, List<String> pendientes) {
        Date ahora = new Date();
        String rol = (u.getRol() != null) ? u.getRol().getNombre() : null;
        return Jwts.builder()
                .subject(u.getUsername())
                .claim("uid", u.getId())
                .claim("rol", rol)
                .claim("sv", u.getSesionVersion())
                .claim("pend", pendientes == null ? List.of() : pendientes)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Datos leer(String token) {
        Claims c = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        Long uid = (c.get("uid") instanceof Number n) ? n.longValue() : null;
        int version = (c.get("sv") instanceof Number n) ? n.intValue() : 0;
        List<String> pendientes = new java.util.ArrayList<>();
        if (c.get("pend") instanceof List<?> l) for (Object o : l) pendientes.add(String.valueOf(o));
        return new Datos(new JwtUser(uid, c.getSubject(), c.get("rol", String.class)), version, pendientes);
    }

    public JwtUser validar(String token) {
        Claims c = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        Long uid = (c.get("uid") instanceof Number n) ? n.longValue() : null;
        return new JwtUser(uid, c.getSubject(), c.get("rol", String.class));
    }
}

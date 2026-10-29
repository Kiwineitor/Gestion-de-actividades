package com.usic.uniFex.model.service;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VisitasPaginaService {

    public static final String FERIA_PUBLICA = "feria-publica";

    private final JdbcTemplate jdbc;

    public long registrar(String pagina) {
        Long total = jdbc.queryForObject(
                "INSERT INTO contador_visitas (pagina, total, ultima_visita) VALUES (?, 1, now()) "
                + "ON CONFLICT (pagina) DO UPDATE SET total = contador_visitas.total + 1, "
                + "ultima_visita = now() RETURNING total",
                Long.class, pagina);
        return total != null ? total : 0L;
    }

    public long total(String pagina) {
        List<Long> fila = jdbc.queryForList(
                "SELECT total FROM contador_visitas WHERE pagina = ?", Long.class, pagina);
        return fila.isEmpty() ? 0L : fila.get(0);
    }
}

package com.usic.uniFex.controller.edicion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/actividades")
@RequiredArgsConstructor
public class ActividadesApiController {

    private final JdbcTemplate jdbc;

    @GetMapping
    public List<Map<String, Object>> listar() {
        return jdbc.query("""
                SELECT * FROM (
                    SELECT 'f-' || e.id AS clave, 'FERIA' AS tipo, e.id, e.nombre, e.activa, e.anio, NULL::timestamp AS fecha,
                           (SELECT count(*) FROM categoria c
                             WHERE c.id_edicion = e.id AND (c."_estado" IS NULL OR c."_estado" <> 'X')) AS categorias
                      FROM edicion e
                    UNION ALL
                    SELECT 'e-' || m.id, 'MESAS', m.id, m.nombre, FALSE, extract(year FROM m.fecha_inicio)::int, m.fecha_inicio, 0
                      FROM mesa_evento m
                     WHERE m."_estado" = 'A'
                ) a
                ORDER BY a.activa DESC, a.anio DESC, a.tipo, a.fecha DESC NULLS LAST, a.id DESC
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("clave", rs.getString("clave"));
            m.put("tipo", rs.getString("tipo"));
            m.put("id", rs.getLong("id"));
            m.put("nombre", rs.getString("nombre"));
            m.put("activa", rs.getBoolean("activa"));
            m.put("anio", rs.getInt("anio"));
            m.put("fecha", rs.getTimestamp("fecha") == null ? null : rs.getTimestamp("fecha").toLocalDateTime().toString());
            m.put("categorias", rs.getLong("categorias"));
            return m;
        });
    }
}

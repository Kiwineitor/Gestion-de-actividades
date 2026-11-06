package com.usic.uniFex.model.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IngresoEventoService {

    private static final String NOMBRE_USUARIO =
            "coalesce(nullif(btrim(concat_ws(' ', vp.nombre, vp.paterno, vp.materno)), ''), u.username)";

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Map<String, Object> resumen(Long eventoId, LocalDateTime desde, LocalDateTime hasta) {
        List<Object> params = new ArrayList<>(List.of(eventoId));
        StringBuilder rango = new StringBuilder();
        if (desde != null) {
            rango.append(" AND x.cuando >= ?");
            params.add(Timestamp.valueOf(desde));
        }
        if (hasta != null) {
            rango.append(" AND x.cuando <= ?");
            params.add(Timestamp.valueOf(hasta));
        }
        List<Map<String, Object>> ingresos = jdbc.query("""
                SELECT * FROM (
                    SELECT a.id, a.cuando, r.codigo AS reserva, p.codigo AS pase, p.numero,
                           (SELECT count(*) FROM mesa_pase t WHERE t.id_reserva = r.id) AS total,
                           coalesce(nullif(btrim(p.tipo), ''), r.tipo_pase) AS tipo,
                           btrim(concat_ws(' ', r.nombre, r.apellidos)) AS titular, r.ci, m.codigo AS mesa, m.zona,
                           %1$s AS registrado_por, r.id_evento, a.sentido, p.nombre AS persona
                      FROM mesa_pase_acceso a
                      JOIN mesa_pase p ON p.id = a.id_pase
                      JOIN mesa_reserva r ON r.id = p.id_reserva
                      JOIN mesa m ON m.id = r.id_mesa
                      LEFT JOIN usuario u ON u.id = a.registrado_por
                      LEFT JOIN persona vp ON vp.id = u.persona_id
                    UNION ALL
                    SELECT -r.id, r.ingreso_en, r.codigo, r.codigo, 1, 1, r.tipo_pase,
                           btrim(concat_ws(' ', r.nombre, r.apellidos)), r.ci, m.codigo, m.zona,
                           %1$s, r.id_evento, 'E', NULL
                      FROM mesa_reserva r
                      JOIN mesa m ON m.id = r.id_mesa
                      LEFT JOIN usuario u ON u.id = r.ingreso_por
                      LEFT JOIN persona vp ON vp.id = u.persona_id
                     WHERE r.ingreso_en IS NOT NULL
                       AND NOT r.retiro_fisico
                       AND NOT EXISTS (SELECT 1 FROM mesa_pase t WHERE t.id_reserva = r.id)
                ) x
                 WHERE x.id_evento = ?%2$s
                 ORDER BY x.cuando DESC, x.id DESC
                """.formatted(NOMBRE_USUARIO, rango), (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("cuando", fecha(rs.getTimestamp("cuando")));
            m.put("reserva", rs.getString("reserva"));
            m.put("pase", rs.getString("pase"));
            m.put("numero", rs.getInt("numero"));
            m.put("total", rs.getInt("total"));
            m.put("tipo", rs.getString("tipo"));
            m.put("titular", rs.getString("titular"));
            m.put("ci", rs.getString("ci"));
            m.put("mesa", rs.getString("mesa"));
            m.put("zona", rs.getString("zona"));
            m.put("registradoPor", rs.getString("registrado_por"));
            m.put("sentido", rs.getString("sentido"));
            m.put("persona", rs.getString("persona"));
            return m;
        }, params.toArray());

        List<Map<String, Object>> reservas = jdbc.query("""
                SELECT r.id, r.codigo, btrim(concat_ws(' ', r.nombre, r.apellidos)) AS titular, r.ci, r.celular,
                       m.codigo AS mesa, m.zona, r.personas, r.tipo_pase,
                       (SELECT count(*) FROM mesa_pase p WHERE p.id_reserva = r.id) AS pases,
                       (SELECT count(*) FROM mesa_pase p WHERE p.id_reserva = r.id AND p.ingreso_en IS NOT NULL) AS usados,
                       (SELECT count(*) FROM mesa_pase p WHERE p.id_reserva = r.id AND p.dentro) AS dentro,
                       coalesce((SELECT max(p.ingreso_en) FROM mesa_pase p WHERE p.id_reserva = r.id), r.ingreso_en) AS ultimo
                  FROM mesa_reserva r
                  JOIN mesa m ON m.id = r.id_mesa
                 WHERE r.id_evento = ? AND r.estado = 'CONFIRMADA' AND NOT r.retiro_fisico
                 ORDER BY ultimo DESC NULLS LAST, m.codigo
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("codigo", rs.getString("codigo"));
            m.put("titular", rs.getString("titular"));
            m.put("ci", rs.getString("ci"));
            m.put("celular", rs.getString("celular"));
            m.put("mesa", rs.getString("mesa"));
            m.put("zona", rs.getString("zona"));
            m.put("personas", rs.getObject("personas", Integer.class));
            m.put("tipoPase", rs.getString("tipo_pase"));
            m.put("pases", rs.getLong("pases"));
            m.put("usados", rs.getLong("usados"));
            m.put("dentro", rs.getLong("dentro"));
            m.put("ultimo", fecha(rs.getTimestamp("ultimo")));
            return m;
        }, eventoId);

        Map<String, Long> porUsuario = new LinkedHashMap<>();
        Map<String, Long> porZona = new LinkedHashMap<>();
        for (Map<String, Object> m : ingresos) {
            if (!"E".equals(m.get("sentido"))) continue;
            porUsuario.merge(m.get("registradoPor") == null ? "Desconocido" : (String) m.get("registradoPor"), 1L, Long::sum);
            porZona.merge(m.get("zona") == null ? "Sin zona" : (String) m.get("zona"), 1L, Long::sum);
        }
        long pases = reservas.stream().mapToLong(m -> Math.max(1L, (Long) m.get("pases"))).sum();
        long usados = reservas.stream().mapToLong(m -> (Long) m.get("usados")).sum()
                + ingresos.stream().filter(m -> ((Long) m.get("id")) < 0).count();

        Map<String, Object> totales = new LinkedHashMap<>();
        long salidas = ingresos.stream().filter(m -> "S".equals(m.get("sentido"))).count();
        totales.put("ingresos", ingresos.size() - salidas);
        totales.put("salidas", salidas);
        totales.put("dentro", reservas.stream().mapToLong(m -> (Long) m.get("dentro")).sum());
        totales.put("pases", pases);
        totales.put("usados", usados);
        totales.put("pendientes", Math.max(0, pases - usados));
        totales.put("reservas", reservas.size());
        totales.put("reservasConIngreso", reservas.stream().filter(m -> m.get("ultimo") != null).count());

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totales", totales);
        r.put("ingresos", ingresos);
        r.put("reservas", reservas);
        r.put("porUsuario", lista(porUsuario));
        r.put("porZona", lista(porZona));
        return r;
    }

    private static List<Map<String, Object>> lista(Map<String, Long> conteo) {
        return conteo.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("nombre", e.getKey());
                    m.put("cantidad", e.getValue());
                    return m;
                })
                .toList();
    }

    private static String fecha(Timestamp t) {
        return t == null ? null : t.toLocalDateTime().toString();
    }
}

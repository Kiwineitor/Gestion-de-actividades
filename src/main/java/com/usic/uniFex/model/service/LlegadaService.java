package com.usic.uniFex.model.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.entity.Edicion;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LlegadaService {

    private static final String NOMBRE = """
            (SELECT COALESCE(NULLIF(btrim(concat_ws(' ', p.nombre, p.paterno, p.materno)), ''), u.username)
               FROM usuario u LEFT JOIN persona p ON p.id = u.persona_id WHERE u.id = %s)""";

    private final JdbcTemplate jdbc;

    public record Resultado(boolean ok, String mensaje, boolean repetido) {
        static Resultado error(String m) { return new Resultado(false, m, false); }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listar(Long edicionId, String texto) {
        String edicion = edicionId == null ? Edicion.SQL_ID_ACTIVA : "?";
        List<Object> params = new ArrayList<>();
        if (edicionId != null) params.add(edicionId);
        String filtro = "";
        String q = texto == null ? "" : texto.trim();
        if (q.matches("[0-9]{1,4}")) {
            filtro = """
                    AND (CAST(i.id AS TEXT) = ? OR EXISTS (SELECT 1 FROM inscripcion_puesto ip2 JOIN puesto p2 ON p2.id = ip2.id_puesto
                                     WHERE ip2.id_inscripcion = i.id AND (ip2."_estado" IS NULL OR ip2."_estado" <> 'X')
                                       AND ltrim(p2.codigo, '0') = ltrim(?, '0')))
                    """;
            params.addAll(List.of(q, q));
        } else if (!q.isEmpty()) {
            String like = "%" + q.toLowerCase().replaceAll("[%_\\\\]", "") + "%";
            filtro = """
                    AND (lower(COALESCE(e.nombre, '')) LIKE ? OR lower(COALESCE(e.representante_legal, '')) LIKE ?
                         OR lower(COALESCE(e.ci_representante, '')) LIKE ? OR CAST(i.id AS TEXT) = ?
                         OR EXISTS (SELECT 1 FROM inscripcion_puesto ip2 JOIN puesto p2 ON p2.id = ip2.id_puesto
                                     LEFT JOIN categoria c2 ON c2.id = p2.id_categoria
                                     WHERE ip2.id_inscripcion = i.id AND (ip2."_estado" IS NULL OR ip2."_estado" <> 'X')
                                       AND (lower(p2.codigo) = ? OR lower(concat_ws(' ', c2.nombre, p2.codigo)) LIKE ?))
                         OR EXISTS (SELECT 1 FROM responsable r JOIN persona pe ON pe.id = r.id_persona
                                     WHERE r.id_entidad = e.id AND (r."_estado" IS NULL OR r."_estado" <> 'X')
                                       AND (lower(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)) LIKE ? OR lower(COALESCE(pe.ci, '')) LIKE ?)))
                    """;
            String exacto = q.toLowerCase();
            params.addAll(List.of(like, like, like, exacto, exacto, like, like, like));
        }
        List<Map<String, Object>> ventas = jdbc.query("""
                SELECT i.id, e.nombre AS cliente, e.representante_legal AS representante, e.ci_representante AS ci,
                       e.celular_representante AS celular,
                       (i.img_comprobante IS NOT NULL AND i.img_comprobante <> '') OR COALESCE(i.cobro_efectivo, FALSE) AS cobrada,
                       (SELECT string_agg(concat_ws(' · ', c.nombre, p.codigo), ', ' ORDER BY c.nombre, lpad(p.codigo, 10, '0'))
                          FROM inscripcion_puesto ip JOIN puesto p ON p.id = ip.id_puesto LEFT JOIN categoria c ON c.id = p.id_categoria
                         WHERE ip.id_inscripcion = i.id AND (ip."_estado" IS NULL OR ip."_estado" <> 'X')) AS espacios,
                       (SELECT min(concat(c.nombre, '|', lpad(p.codigo, 10, '0')))
                          FROM inscripcion_puesto ip JOIN puesto p ON p.id = ip.id_puesto LEFT JOIN categoria c ON c.id = p.id_categoria
                         WHERE ip.id_inscripcion = i.id AND (ip."_estado" IS NULL OR ip."_estado" <> 'X')) AS orden,
                       (SELECT COALESCE(sum(ip.costo), 0) FROM inscripcion_puesto ip
                         WHERE ip.id_inscripcion = i.id AND (ip."_estado" IS NULL OR ip."_estado" <> 'X')) AS total,
                       (SELECT COALESCE(sum(COALESCE(c.personas_por_puesto, 1)), 0)
                          FROM inscripcion_puesto ip JOIN puesto p ON p.id = ip.id_puesto LEFT JOIN categoria c ON c.id = p.id_categoria
                         WHERE ip.id_inscripcion = i.id AND (ip."_estado" IS NULL OR ip."_estado" <> 'X')) AS incluidas,
                       (SELECT count(*) FROM responsable r WHERE r.id_entidad = e.id AND (r."_estado" IS NULL OR r."_estado" <> 'X')) AS registradas,
                       COALESCE(ed.usa_credenciales AND ed.credencial_representante, FALSE) AS con_credenciales,
                       l.llego_en, l.personas AS llegaron, l.nota, %s AS recibio
                  FROM inscripcion i
                  JOIN entidad e ON e.id = i.id_entidad
                  LEFT JOIN edicion ed ON ed.id = i.id_edicion
                  LEFT JOIN llegada l ON l.id_inscripcion = i.id AND l.dia = CURRENT_DATE
                 WHERE i.id_edicion = %s AND i.fecha_cancelacion IS NULL AND (i."_estado" IS NULL OR i."_estado" <> 'X')
                   %s
                 ORDER BY (l.llego_en IS NOT NULL), orden NULLS LAST, i.id
                 LIMIT 300
                """.formatted(NOMBRE.formatted("l.registrado_por"), edicion, filtro), (rs, n) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("cliente", rs.getString("cliente"));
            m.put("representante", rs.getString("representante"));
            m.put("ci", rs.getString("ci"));
            m.put("celular", rs.getString("celular"));
            m.put("espacios", rs.getString("espacios"));
            m.put("total", rs.getBigDecimal("total"));
            m.put("cobrada", rs.getBoolean("cobrada") || BigDecimal.ZERO.compareTo(rs.getBigDecimal("total")) == 0);
            m.put("incluidas", rs.getInt("incluidas"));
            m.put("registradas", rs.getInt("registradas"));
            m.put("conCredenciales", rs.getBoolean("con_credenciales"));
            java.sql.Timestamp llego = rs.getTimestamp("llego_en");
            m.put("llegoEn", llego == null ? null : llego.toLocalDateTime().toString());
            m.put("llegaron", rs.getObject("llegaron"));
            m.put("nota", rs.getString("nota"));
            m.put("recibio", rs.getString("recibio"));
            return m;
        }, params.toArray());

        Object[] solo = edicionId == null ? new Object[0] : new Object[] { edicionId };
        Map<String, Object> cuenta = jdbc.queryForMap("""
                SELECT count(*) AS ventas, count(l.id) AS llegaron
                  FROM inscripcion i LEFT JOIN llegada l ON l.id_inscripcion = i.id AND l.dia = CURRENT_DATE
                 WHERE i.id_edicion = %s AND i.fecha_cancelacion IS NULL AND (i."_estado" IS NULL OR i."_estado" <> 'X')
                """.formatted(edicion), solo);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ventas", ((Number) cuenta.get("ventas")).longValue());
        r.put("llegaron", ((Number) cuenta.get("llegaron")).longValue());
        r.put("lista", ventas);
        return r;
    }

    @Transactional
    public Resultado confirmar(Long inscripcionId, Integer personas, String nota, Long usuarioId) {
        List<Map<String, Object>> venta = jdbc.queryForList("""
                SELECT i.id, i.fecha_cancelacion, i."_estado" AS estado, e.nombre
                  FROM inscripcion i LEFT JOIN entidad e ON e.id = i.id_entidad WHERE i.id = ?
                """, inscripcionId);
        if (venta.isEmpty()) return Resultado.error("La venta no existe");
        Map<String, Object> v = venta.getFirst();
        if (v.get("fecha_cancelacion") != null || "X".equals(v.get("estado"))) {
            return Resultado.error("Esta venta está anulada: no tiene lugar reservado");
        }
        if (personas != null && (personas < 0 || personas > 500)) return Resultado.error("Cantidad de personas no válida");
        String texto = nota == null || nota.isBlank() ? null : nota.trim();
        if (texto != null && texto.length() > 300) texto = texto.substring(0, 300);
        int n = jdbc.update("""
                INSERT INTO llegada (id_inscripcion, personas, nota, registrado_por) VALUES (?, ?, ?, ?)
                ON CONFLICT (id_inscripcion, dia) DO NOTHING
                """, inscripcionId, personas, texto, usuarioId);
        if (n == 0) {
            Map<String, Object> previa = jdbc.queryForMap("""
                    SELECT to_char(l.llego_en, 'HH24:MI') AS hora, %s AS recibio
                      FROM llegada l WHERE l.id_inscripcion = ? AND l.dia = CURRENT_DATE
                    """.formatted(NOMBRE.formatted("l.registrado_por")), inscripcionId);
            return new Resultado(false, "Ya se había confirmado su llegada a las " + previa.get("hora")
                    + (previa.get("recibio") != null ? " (lo recibió " + previa.get("recibio") + ")" : ""), true);
        }
        HistorialService.anotarResumen(String.valueOf(v.get("nombre")));
        return new Resultado(true, "Llegada confirmada: " + v.get("nombre"), false);
    }

    @Transactional
    public Resultado deshacer(Long inscripcionId) {
        int n = jdbc.update("DELETE FROM llegada WHERE id_inscripcion = ? AND dia = CURRENT_DATE", inscripcionId);
        if (n == 0) return Resultado.error("Hoy no figura su llegada");
        return new Resultado(true, "Se quitó la llegada", false);
    }
}

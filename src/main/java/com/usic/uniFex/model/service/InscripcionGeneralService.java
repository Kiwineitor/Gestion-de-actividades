package com.usic.uniFex.model.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InscripcionGeneralService {

    private static final String NOMBRE_USUARIO =
            "coalesce(nullif(btrim(concat_ws(' ', vp.nombre, vp.paterno, vp.materno)), ''), u.username)";

    private final JdbcTemplate jdbc;

    public record Filtros(String actividad, String categoria, Long vendedor, String estado, String q, Long soloDe) {
    }

    record Actividad(String tipo, Long id) {
        static Actividad de(String clave) {
            if (clave == null || clave.isBlank()) return new Actividad(null, null);
            String c = clave.trim().toLowerCase();
            if (c.equals("ferias")) return new Actividad("FERIA", null);
            if (c.matches("f-\\d+")) return new Actividad("FERIA", Long.parseLong(c.substring(2)));
            return new Actividad(null, null);
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar(Filtros f) {
        Actividad a = Actividad.de(f.actividad());
        String estado = f.estado() == null ? "" : f.estado().trim().toUpperCase();
        List<Map<String, Object>> filas = new ArrayList<>();
        filas.addAll(ferias(a, f));
        String q = normal(f.q());
        return filas.stream()
                .filter(m -> coincideEstado(estado, (String) m.get("estadoGeneral")))
                .filter(m -> q.isEmpty() || normal(buscable(m)).contains(q))
                .sorted(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("fecha") == null ? "" : m.get("fecha")))
                        .reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> opciones(String actividad, Long soloDe) {
        Actividad a = Actividad.de(actividad);
        Map<String, Object> r = new LinkedHashMap<>();
        List<Map<String, Object>> categorias = new ArrayList<>();
        if ("FERIA".equals(a.tipo()) && a.id() != null) {
            categorias = jdbc.query("""
                    SELECT c.id, c.nombre FROM categoria c
                     WHERE c.id_edicion = ? AND (c."_estado" IS NULL OR c."_estado" <> 'X')
                     ORDER BY c.nombre
                    """, (rs, i) -> opcion(String.valueOf(rs.getLong("id")), rs.getString("nombre")), a.id());
        }
        r.put("categorias", categorias);
        r.put("categoriaEs", "categoría");

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT DISTINCT u.id, " + NOMBRE_USUARIO + " AS nombre FROM (");
        StringBuilder p = new StringBuilder("SELECT i.\"_registro_id_usuario\" AS uid FROM inscripcion i WHERE i.\"_registro_id_usuario\" IS NOT NULL");
        if (a.id() != null) {
            p.append(" AND i.id_edicion = ?");
            params.add(a.id());
        }
        sql.append(p).append(") x JOIN usuario u ON u.id = x.uid LEFT JOIN persona vp ON vp.id = u.persona_id");
        if (soloDe != null) {
            sql.append(" WHERE u.id = ?");
            params.add(soloDe);
        }
        sql.append(" ORDER BY nombre");
        r.put("vendedores", jdbc.query(sql.toString(), (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("nombre", rs.getString("nombre"));
            return m;
        }, params.toArray()));
        return r;
    }

    private List<Map<String, Object>> ferias(Actividad a, Filtros f) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT i.id, coalesce(i.nota_codigo, 'V-' || i.id) AS codigo, ed.id AS edicion_id, ed.nombre AS actividad,
                       i.fecha_compra AS fecha, e.nombre AS cliente, e.representante_legal AS representante,
                       e.ci_representante AS ci, e.celular_representante AS celular,
                       pu.espacios, pu.categorias, coalesce(pu.monto, 0) + coalesce(ex.monto, 0) AS monto,
                       (i.img_comprobante IS NOT NULL OR i.cobro_efectivo) AS pagado, i.pago_contado, i.cobro_efectivo,
                       (i."_estado" = 'X') AS anulada, i.inscripcion_estado AS estado,
                       u.id AS vendedor_id, %s AS vendedor,
                       EXISTS (SELECT 1 FROM interesado_stand s WHERE s.id_inscripcion = i.id) AS en_linea
                  FROM inscripcion i
                  JOIN entidad e ON e.id = i.id_entidad
                  LEFT JOIN edicion ed ON ed.id = i.id_edicion
                  LEFT JOIN usuario u ON u.id = i."_registro_id_usuario"
                  LEFT JOIN persona vp ON vp.id = u.persona_id
                  LEFT JOIN LATERAL (
                        SELECT string_agg(coalesce(c.nombre || ' ', '') || p.codigo, ', ' ORDER BY c.nombre, p.codigo) AS espacios,
                               string_agg(DISTINCT c.nombre, ', ') AS categorias,
                               sum(coalesce(ip.costo, 0)) AS monto,
                               array_agg(DISTINCT c.id) AS categoria_ids
                          FROM inscripcion_puesto ip
                          JOIN puesto p ON p.id = ip.id_puesto
                          LEFT JOIN categoria c ON c.id = p.id_categoria
                         WHERE ip.id_inscripcion = i.id
                           AND (i."_estado" = 'X' OR ip."_estado" IS NULL OR ip."_estado" <> 'X')) pu ON TRUE
                  LEFT JOIN LATERAL (
                        SELECT sum(coalesce(r.monto_extra, 0)) AS monto
                          FROM responsable r
                         WHERE r.id_entidad = i.id_entidad AND r.es_extra
                           AND (r."_estado" IS NULL OR r."_estado" <> 'X')) ex ON TRUE
                 WHERE TRUE
                """.formatted(NOMBRE_USUARIO));
        if (a.id() != null) {
            sql.append(" AND i.id_edicion = ?");
            params.add(a.id());
        }
        Long categoria = numero(f.categoria());
        if (categoria != null && a.id() != null) {
            sql.append(" AND ? = ANY(pu.categoria_ids)");
            params.add(categoria);
        }
        if (f.vendedor() != null) {
            sql.append(" AND i.\"_registro_id_usuario\" = ?");
            params.add(f.vendedor());
        }
        if (f.soloDe() != null) {
            sql.append(" AND i.\"_registro_id_usuario\" = ?");
            params.add(f.soloDe());
        }
        return jdbc.query(sql.toString(), (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tipo", "FERIA");
            m.put("id", rs.getLong("id"));
            m.put("codigo", rs.getString("codigo"));
            Long ed = rs.getObject("edicion_id", Long.class);
            m.put("actividadClave", ed == null ? null : "f-" + ed);
            m.put("actividad", rs.getString("actividad"));
            m.put("fecha", fecha(rs.getTimestamp("fecha")));
            m.put("cliente", rs.getString("cliente"));
            m.put("representante", rs.getString("representante"));
            m.put("ci", rs.getString("ci"));
            m.put("celular", rs.getString("celular"));
            m.put("espacios", rs.getString("espacios"));
            m.put("categoria", rs.getString("categorias"));
            m.put("personas", null);
            m.put("monto", rs.getBigDecimal("monto"));
            m.put("pagado", rs.getBoolean("pagado"));
            m.put("formaPago", rs.getBoolean("cobro_efectivo") ? "Efectivo" : rs.getBoolean("pago_contado") ? "Al contado" : rs.getBoolean("pagado") ? "Depósito / transferencia" : null);
            boolean anulada = rs.getBoolean("anulada");
            m.put("estadoGeneral", anulada ? "ANULADA" : "VIGENTE");
            m.put("estado", anulada ? "Anulada" : "Vigente");
            vendedor(m, rs);
            m.put("canal", rs.getBoolean("en_linea") ? "EN_LINEA" : "EN_PERSONA");
            return m;
        }, params.toArray());
    }

    private static boolean coincideEstado(String filtro, String estadoGeneral) {
        if (filtro.isEmpty()) return "VIGENTE".equals(estadoGeneral) || "POR_VALIDAR".equals(estadoGeneral);
        if ("TODAS".equals(filtro)) return true;
        return filtro.equals(estadoGeneral);
    }

    private static void vendedor(Map<String, Object> m, ResultSet rs) throws SQLException {
        m.put("vendedorId", rs.getObject("vendedor_id", Long.class));
        m.put("vendedor", rs.getString("vendedor"));
    }

    private static Map<String, Object> opcion(String id, String nombre) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("nombre", nombre);
        return m;
    }

    private static String buscable(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder();
        for (String k : List.of("codigo", "cliente", "representante", "ci", "celular", "espacios", "categoria", "vendedor", "actividad")) {
            Object v = m.get(k);
            if (v != null) sb.append(v).append(' ');
        }
        sb.append(m.get("id"));
        return sb.toString();
    }

    private static String normal(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    private static Long numero(String s) {
        if (s == null || !s.trim().matches("\\d+")) return null;
        return Long.parseLong(s.trim());
    }

    private static String fecha(Timestamp t) {
        return t == null ? null : t.toLocalDateTime().toString();
    }
}

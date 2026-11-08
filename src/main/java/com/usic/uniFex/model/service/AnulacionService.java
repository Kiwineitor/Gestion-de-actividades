package com.usic.uniFex.model.service;

import java.util.ArrayList;
import java.util.Comparator;
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
public class AnulacionService {

    private static final String NOMBRE = """
            (SELECT COALESCE(NULLIF(btrim(concat_ws(' ', p.nombre, p.paterno, p.materno)), ''), u.username)
               FROM usuario u LEFT JOIN persona p ON p.id = u.persona_id WHERE u.id = %s)""";

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar(Long edicionId, Long soloDe) {
        String edicion = edicionId == null ? Edicion.SQL_ID_ACTIVA : "?";
        List<Object> params = new ArrayList<>();
        if (edicionId != null) params.add(edicionId);
        if (soloDe != null) params.add(soloDe);
        List<Map<String, Object>> filas = new ArrayList<>(jdbc.query("""
                SELECT i.id, i.fecha_cancelacion AS cuando, COALESCE(e.nombre, '—') AS cliente, e.celular_representante AS celular,
                       i.motivo_cancelacion AS motivo, i.origen_cancelacion AS origen,
                       %s AS anulo, %s AS atendio,
                       EXISTS (SELECT 1 FROM interesado_stand s WHERE s.id_inscripcion = i.id) AS en_linea,
                       (SELECT string_agg(c.nombre || ' ' || p.codigo, ', ' ORDER BY c.nombre, p.codigo)
                          FROM inscripcion_puesto ip JOIN puesto p ON p.id = ip.id_puesto
                          LEFT JOIN categoria c ON c.id = p.id_categoria WHERE ip.id_inscripcion = i.id) AS detalle,
                       (SELECT COALESCE(sum(ip.costo), 0) FROM inscripcion_puesto ip WHERE ip.id_inscripcion = i.id) AS monto
                  FROM inscripcion i LEFT JOIN entidad e ON e.id = i.id_entidad
                 WHERE i.id_edicion = %s AND i.fecha_cancelacion IS NOT NULL %s
                """.formatted(NOMBRE.formatted("i.cancelada_por_id_usuario"), NOMBRE.formatted("i.\"_registro_id_usuario\""),
                edicion, soloDe == null ? "" : "AND i.\"_registro_id_usuario\" = ?"),
                (rs, n) -> fila("VENTA", rs.getBoolean("en_linea") ? "Venta en línea anulada" : "Venta en persona anulada",
                        rs.getLong("id"), rs.getTimestamp("cuando"), rs.getString("cliente"), rs.getString("celular"),
                        rs.getString("detalle"), rs.getBigDecimal("monto"), rs.getString("anulo"), rs.getString("motivo"),
                        rs.getString("atendio"), rs.getBoolean("en_linea") ? "EN_LINEA" : "EN_PERSONA"),
                params.toArray()));
        if (soloDe != null) {
            filas.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("cuando"))).reversed());
            return filas;
        }
        Object[] solo = edicionId == null ? new Object[0] : new Object[] { edicionId };
        filas.addAll(jdbc.query("""
                SELECT s.id, COALESCE(s.descartado_en, s.fecha_registro) AS cuando, COALESCE(NULLIF(s.empresa, ''), s.nombre_completo) AS cliente,
                       s.celular, s.motivo_descarte AS motivo, %s AS anulo,
                       concat_ws(' ', c.nombre, p.codigo) AS detalle, COALESCE(p.precio, c.precio_base, 0) AS monto
                  FROM interesado_stand s JOIN categoria c ON c.id = s.id_categoria LEFT JOIN puesto p ON p.id = s.id_puesto
                 WHERE c.id_edicion = %s AND s.estado = 'DESCARTADO'
                """.formatted(NOMBRE.formatted("s.descartado_por"), edicion),
                (rs, n) -> fila("SOLICITUD", "Solicitud en línea rechazada", rs.getLong("id"), rs.getTimestamp("cuando"),
                        rs.getString("cliente"), rs.getString("celular"), rs.getString("detalle"), rs.getBigDecimal("monto"),
                        rs.getString("anulo"), rs.getString("motivo"), "Página pública", "EN_LINEA"),
                solo));
        filas.addAll(jdbc.query("""
                SELECT x.id, COALESCE(x.validado_en, x.creado_en) AS cuando, x.nombre AS cliente, x.celular, x.motivo, x.estado,
                       x.codigo, x.total AS monto, x.registrado_por IS NULL AS en_linea, %s AS anulo, %s AS atendio,
                       (SELECT string_agg(d.cantidad || ' × ' || t.nombre, ', ') FROM entrada_compra_detalle d
                          JOIN entrada_tipo t ON t.id = d.id_tipo WHERE d.id_compra = x.id) AS detalle
                  FROM entrada_compra x WHERE x.id_edicion = %s AND x.estado IN ('RECHAZADA', 'ANULADA')
                """.formatted(NOMBRE.formatted("x.validado_por"), NOMBRE.formatted("x.registrado_por"), edicion),
                (rs, n) -> fila("ENTRADAS", "RECHAZADA".equals(rs.getString("estado")) ? "Compra de entradas rechazada" : "Compra de entradas anulada",
                        rs.getLong("id"), rs.getTimestamp("cuando"), rs.getString("cliente"), rs.getString("celular"),
                        (rs.getString("codigo") == null ? "" : rs.getString("codigo") + " · ") + (rs.getString("detalle") == null ? "" : rs.getString("detalle")),
                        rs.getBigDecimal("monto"), rs.getString("anulo"), rs.getString("motivo"),
                        rs.getBoolean("en_linea") ? "Página pública" : rs.getString("atendio"),
                        rs.getBoolean("en_linea") ? "EN_LINEA" : "EN_PERSONA"),
                solo));
        filas.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("cuando"))).reversed());
        return filas;
    }

    private static Map<String, Object> fila(String tipo, String etiqueta, long id, java.sql.Timestamp cuando, String cliente,
                                            String celular, String detalle, java.math.BigDecimal monto, String anulo,
                                            String motivo, String atendio, String canal) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", tipo);
        m.put("etiqueta", etiqueta);
        m.put("id", id);
        m.put("cuando", cuando == null ? null : cuando.toLocalDateTime().toString());
        m.put("cliente", cliente);
        m.put("celular", celular);
        m.put("detalle", detalle);
        m.put("monto", monto);
        m.put("anulo", anulo);
        m.put("motivo", motivo);
        m.put("atendio", atendio);
        m.put("canal", canal);
        return m;
    }
}

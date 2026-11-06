package com.usic.uniFex.model.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.service.TablaExportService.Columna;
import com.usic.uniFex.model.service.TablaExportService.Tabla;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReporteEventoService {

    private static final String NOMBRE_USUARIO =
            "coalesce(nullif(btrim(concat_ws(' ', vp.nombre, vp.paterno, vp.materno)), ''), u.username)";

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Map<String, Object> resumen(Long eventoId) {
        List<Map<String, Object>> ev = jdbc.query("""
                SELECT id, nombre, local_nombre, fecha_inicio FROM mesa_evento WHERE id = ? AND "_estado" = 'A'
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("nombre", rs.getString("nombre"));
            m.put("local", rs.getString("local_nombre"));
            m.put("fecha", fecha(rs.getTimestamp("fecha_inicio")));
            return m;
        }, eventoId);
        if (ev.isEmpty()) return null;

        Map<String, Object> totales = jdbc.queryForMap("""
                SELECT (SELECT count(*) FROM mesa m WHERE m.id_evento = ? AND m."_estado" = 'A') AS mesas,
                       (SELECT count(*) FROM mesa m WHERE m.id_evento = ? AND m."_estado" = 'A' AND m.estado = 'O') AS vendidas,
                       (SELECT count(*) FROM mesa m WHERE m.id_evento = ? AND m."_estado" = 'A' AND m.estado IN ('T', 'R')) AS apartadas,
                       (SELECT count(*) FROM mesa m WHERE m.id_evento = ? AND m."_estado" = 'A' AND m.estado = 'L') AS libres,
                       (SELECT count(*) FROM mesa m WHERE m.id_evento = ? AND m."_estado" = 'A' AND m.estado = 'X') AS fuera_de_venta,
                       (SELECT coalesce(sum(r.monto), 0) FROM mesa_reserva r WHERE r.id_evento = ? AND r.estado = 'CONFIRMADA') AS recaudado,
                       (SELECT coalesce(sum(r.personas), 0) FROM mesa_reserva r WHERE r.id_evento = ? AND r.estado = 'CONFIRMADA') AS personas,
                       (SELECT count(*) FROM mesa_reserva r WHERE r.id_evento = ? AND r.estado = 'EN_REVISION') AS por_validar,
                       (SELECT coalesce(sum(r.monto), 0) FROM mesa_reserva r WHERE r.id_evento = ? AND r.estado = 'EN_REVISION') AS monto_por_validar,
                       (SELECT count(*) FROM mesa_pase p JOIN mesa_reserva r ON r.id = p.id_reserva
                         WHERE r.id_evento = ? AND r.estado = 'CONFIRMADA') AS pases,
                       (SELECT count(*) FROM mesa_pase p JOIN mesa_reserva r ON r.id = p.id_reserva
                         WHERE r.id_evento = ? AND p.ingreso_en IS NOT NULL) AS pases_usados,
                       (SELECT count(*) FROM mesa_reserva r WHERE r.id_evento = ? AND r.ingreso_en IS NOT NULL
                         AND NOT r.retiro_fisico) AS reservas_con_ingreso
                """, eventoId, eventoId, eventoId, eventoId, eventoId, eventoId, eventoId, eventoId, eventoId, eventoId,
                eventoId, eventoId);
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("mesas", entero(totales.get("mesas")));
        t.put("vendidas", entero(totales.get("vendidas")));
        t.put("apartadas", entero(totales.get("apartadas")));
        t.put("libres", entero(totales.get("libres")));
        t.put("fueraDeVenta", entero(totales.get("fuera_de_venta")));
        t.put("recaudado", totales.get("recaudado"));
        t.put("personas", entero(totales.get("personas")));
        t.put("porValidar", entero(totales.get("por_validar")));
        t.put("montoPorValidar", totales.get("monto_por_validar"));
        t.put("pases", entero(totales.get("pases")));
        t.put("pasesUsados", entero(totales.get("pases_usados")));
        t.put("reservasConIngreso", entero(totales.get("reservas_con_ingreso")));

        Map<String, Object> porEstado = new LinkedHashMap<>();
        jdbc.query("SELECT estado, count(*) AS n FROM mesa_reserva WHERE id_evento = ? GROUP BY estado ORDER BY estado",
                rs -> { porEstado.put(rs.getString("estado"), rs.getLong("n")); }, eventoId);

        List<Map<String, Object>> porZona = jdbc.query("""
                SELECT coalesce(nullif(btrim(m.zona), ''), 'Sin zona') AS zona, count(*) AS mesas,
                       count(*) FILTER (WHERE m.estado = 'O') AS vendidas,
                       coalesce(sum(CASE WHEN m.id = r.id_mesa_adicional THEN r.monto_mesa_adicional
                           ELSE r.monto - coalesce(r.monto_mesa_adicional, 0) END)
                           FILTER (WHERE r.estado = 'CONFIRMADA'), 0) AS recaudado,
                       coalesce(sum(CASE WHEN m.id = r.id_mesa_adicional THEN r.manillas_mesa_adicional
                           ELSE r.personas - coalesce(r.manillas_mesa_adicional, 0) END)
                           FILTER (WHERE r.estado = 'CONFIRMADA'), 0) AS personas
                  FROM mesa m
                  LEFT JOIN mesa_reserva r ON r.id = m.id_reserva
                 WHERE m.id_evento = ? AND m."_estado" = 'A'
                 GROUP BY 1 ORDER BY 1
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("zona", rs.getString("zona"));
            m.put("mesas", rs.getLong("mesas"));
            m.put("vendidas", rs.getLong("vendidas"));
            m.put("recaudado", rs.getBigDecimal("recaudado"));
            m.put("personas", rs.getLong("personas"));
            return m;
        }, eventoId);

        List<Map<String, Object>> porTipo = jdbc.query("""
                SELECT coalesce(nullif(btrim(p.tipo), ''), 'General') AS tipo, count(*) AS pases,
                       count(*) FILTER (WHERE p.ingreso_en IS NOT NULL) AS usados
                  FROM mesa_pase p JOIN mesa_reserva r ON r.id = p.id_reserva
                 WHERE r.id_evento = ? AND r.estado = 'CONFIRMADA'
                 GROUP BY 1 ORDER BY 1
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tipo", rs.getString("tipo"));
            m.put("pases", rs.getLong("pases"));
            m.put("usados", rs.getLong("usados"));
            return m;
        }, eventoId);

        List<Map<String, Object>> porUsuario = jdbc.query("""
                SELECT u.id, %s AS nombre,
                       count(*) FILTER (WHERE r.validado_por = u.id AND r.estado IN ('CONFIRMADA', 'CANCELADA')) AS validadas,
                       count(*) FILTER (WHERE r.validado_por = u.id AND r.estado = 'RECHAZADA') AS rechazadas,
                       count(*) FILTER (WHERE r.registrado_por = u.id) AS registradas,
                       coalesce(sum(r.monto) FILTER (WHERE r.estado = 'CONFIRMADA'
                           AND u.id = coalesce(r.registrado_por, r.validado_por)), 0) AS recaudado
                  FROM mesa_reserva r
                  JOIN usuario u ON u.id IN (r.validado_por, r.registrado_por)
                  LEFT JOIN persona vp ON vp.id = u.persona_id
                 WHERE r.id_evento = ?
                 GROUP BY u.id, 2 ORDER BY recaudado DESC, nombre
                """.formatted(NOMBRE_USUARIO), (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("nombre", rs.getString("nombre"));
            m.put("validadas", rs.getLong("validadas"));
            m.put("rechazadas", rs.getLong("rechazadas"));
            m.put("registradas", rs.getLong("registradas"));
            m.put("recaudado", rs.getBigDecimal("recaudado"));
            return m;
        }, eventoId);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("evento", ev.get(0));
        r.put("totales", t);
        r.put("porEstado", porEstado);
        r.put("porZona", porZona);
        r.put("porTipoPase", porTipo);
        r.put("porUsuario", porUsuario);
        r.put("reservas", reservas(eventoId, null));
        return r;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> reservas(Long eventoId, Long deUsuario) {
        List<Object> params = new ArrayList<>(List.of(eventoId));
        String filtro = "";
        if (deUsuario != null) {
            filtro = " AND (r.registrado_por = ? OR r.validado_por = ?)";
            params.add(deUsuario);
            params.add(deUsuario);
        }
        return jdbc.query("""
                SELECT r.id, r.codigo, btrim(concat_ws(' ', r.nombre, r.apellidos)) AS cliente, r.ci, r.celular,
                       concat_ws(', ', m.codigo, a.codigo) AS mesa, m.zona, r.personas, r.tipo_pase, r.monto,
                       r.estado, r.creado_en, r.validado_en, r.registrado_por IS NOT NULL AS en_oficina, r.motivo,
                       CASE WHEN NOT r.retiro_fisico THEN r.ingreso_en END AS ingreso_en,
                       (SELECT count(*) FROM mesa_pase p WHERE p.id_reserva = r.id) AS pases,
                       (SELECT count(*) FROM mesa_pase p WHERE p.id_reserva = r.id AND p.ingreso_en IS NOT NULL) AS pases_usados,
                       %s AS atendio
                  FROM mesa_reserva r
                  JOIN mesa m ON m.id = r.id_mesa
                  LEFT JOIN mesa a ON a.id = r.id_mesa_adicional
                  LEFT JOIN usuario u ON u.id = coalesce(r.registrado_por, r.validado_por)
                  LEFT JOIN persona vp ON vp.id = u.persona_id
                 WHERE r.id_evento = ?%s
                 ORDER BY CASE r.estado WHEN 'EN_REVISION' THEN 0 WHEN 'CONFIRMADA' THEN 1 ELSE 2 END, r.creado_en DESC
                """.formatted(NOMBRE_USUARIO, filtro), (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("codigo", rs.getString("codigo"));
            m.put("cliente", rs.getString("cliente"));
            m.put("ci", rs.getString("ci"));
            m.put("celular", rs.getString("celular"));
            m.put("mesa", rs.getString("mesa"));
            m.put("zona", rs.getString("zona"));
            m.put("personas", rs.getObject("personas", Integer.class));
            m.put("tipoPase", rs.getString("tipo_pase"));
            m.put("monto", rs.getBigDecimal("monto"));
            m.put("estado", rs.getString("estado"));
            m.put("creadoEn", fecha(rs.getTimestamp("creado_en")));
            m.put("validadoEn", fecha(rs.getTimestamp("validado_en")));
            m.put("enOficina", rs.getBoolean("en_oficina"));
            m.put("motivo", rs.getString("motivo"));
            m.put("ingresoEn", fecha(rs.getTimestamp("ingreso_en")));
            m.put("pases", rs.getLong("pases"));
            m.put("pasesUsados", rs.getLong("pases_usados"));
            m.put("atendio", rs.getString("atendio"));
            return m;
        }, params.toArray());
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<Tabla> tablas(Long eventoId) {
        Map<String, Object> r = resumen(eventoId);
        if (r == null) return null;
        Map<String, Object> ev = (Map<String, Object>) r.get("evento");
        Map<String, Object> t = (Map<String, Object>) r.get("totales");
        String nombre = String.valueOf(ev.get("nombre"));
        List<String> notas = List.of(ev.get("local") + " · " + String.valueOf(ev.get("fecha")).replace('T', ' '));

        List<List<String>> filasResumen = new ArrayList<>();
        filasResumen.add(List.of("Mesas", s(t.get("mesas"))));
        filasResumen.add(List.of("Vendidas", s(t.get("vendidas"))));
        filasResumen.add(List.of("Apartadas o por validar", s(t.get("apartadas"))));
        filasResumen.add(List.of("Libres", s(t.get("libres"))));
        filasResumen.add(List.of("Fuera de venta", s(t.get("fueraDeVenta"))));
        filasResumen.add(List.of("Recaudado (Bs)", s(t.get("recaudado"))));
        filasResumen.add(List.of("Pagos por validar (Bs)", s(t.get("montoPorValidar"))));
        filasResumen.add(List.of("Personas confirmadas", s(t.get("personas"))));
        filasResumen.add(List.of("Pases emitidos", s(t.get("pases"))));
        filasResumen.add(List.of("Pases usados en puerta", s(t.get("pasesUsados"))));

        List<List<String>> zonas = ((List<Map<String, Object>>) r.get("porZona")).stream()
                .map(z -> List.of(s(z.get("zona")), s(z.get("mesas")), s(z.get("vendidas")), s(z.get("personas")), s(z.get("recaudado"))))
                .toList();
        List<List<String>> tipos = ((List<Map<String, Object>>) r.get("porTipoPase")).stream()
                .map(z -> List.of(s(z.get("tipo")), s(z.get("pases")), s(z.get("usados"))))
                .toList();
        List<List<String>> usuarios = ((List<Map<String, Object>>) r.get("porUsuario")).stream()
                .map(z -> List.of(s(z.get("nombre")), s(z.get("registradas")), s(z.get("validadas")), s(z.get("rechazadas")),
                        s(z.get("recaudado"))))
                .toList();
        List<List<String>> reservas = ((List<Map<String, Object>>) r.get("reservas")).stream()
                .map(z -> List.of(s(z.get("codigo")), s(z.get("cliente")), s(z.get("ci")), s(z.get("celular")),
                        s(z.get("mesa")), s(z.get("zona")), s(z.get("personas")), s(z.get("tipoPase")), s(z.get("monto")),
                        etiqueta(s(z.get("estado"))), s(z.get("atendio")), s(z.get("pasesUsados")) + " / " + s(z.get("pases")),
                        s(z.get("creadoEn")).replace('T', ' ')))
                .toList();

        return List.of(
                new Tabla(nombre + " · Resumen", notas, List.of(Columna.texto("Concepto"), Columna.numero("Valor")), filasResumen),
                new Tabla("Por zona", List.of(), List.of(Columna.texto("Zona"), Columna.entero("Mesas"), Columna.entero("Vendidas"),
                        Columna.entero("Personas"), Columna.numero("Recaudado (Bs)")), zonas),
                new Tabla("Por tipo de pase", List.of(), List.of(Columna.texto("Tipo"), Columna.entero("Pases"),
                        Columna.entero("Usados")), tipos),
                new Tabla("Por usuario", List.of(), List.of(Columna.texto("Usuario"), Columna.entero("Registradas"),
                        Columna.entero("Validadas"), Columna.entero("Rechazadas"), Columna.numero("Recaudado (Bs)")), usuarios),
                new Tabla("Reservas", List.of(), List.of(Columna.texto("Código"), Columna.texto("Cliente"), Columna.texto("C.I."),
                        Columna.texto("Celular"), Columna.texto("Mesa"), Columna.texto("Zona"), Columna.entero("Personas"),
                        Columna.texto("Tipo de pase"), Columna.numero("Monto (Bs)"), Columna.texto("Estado"),
                        Columna.texto("Atendió"), Columna.texto("Ingresos"), Columna.texto("Fecha")), reservas));
    }

    public static String etiqueta(String estado) {
        return switch (estado) {
            case "CONFIRMADA" -> "Confirmada";
            case "EN_REVISION" -> "Pago por validar";
            case "BLOQUEADA" -> "Apartada, sin pago";
            case "RECHAZADA" -> "Rechazada";
            case "VENCIDA" -> "Vencida";
            case "CANCELADA" -> "Cancelada";
            default -> estado;
        };
    }

    private static String s(Object o) {
        if (o == null) return "";
        if (o instanceof BigDecimal b) return b.stripTrailingZeros().toPlainString();
        return String.valueOf(o);
    }

    private static long entero(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }

    private static String fecha(Timestamp t) {
        return t == null ? null : t.toLocalDateTime().toString();
    }
}

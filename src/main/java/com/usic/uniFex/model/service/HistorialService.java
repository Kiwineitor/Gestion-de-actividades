package com.usic.uniFex.model.service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class HistorialService {

    public static final String ATRIBUTO_CAMBIOS = "historial.cambios";
    public static final String ATRIBUTO_RESUMEN = "historial.resumen";
    public static final String ATRIBUTO_ERROR = "historial.error";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public record Entrada(Long usuarioId, String usuario, String rol, String modulo, String accion, String metodo,
                          String ruta, Long registroId, Integer estadoHttp, boolean ok, String ip, Map<String, Object> detalle) {
    }

    public record Filtros(Long usuarioId, String modulo, LocalDate desde, LocalDate hasta, String texto, Boolean soloErrores,
                          Long antesDe, Integer limite) {
    }

    public static void anotarCambio(String campo, Object antes, Object despues) {
        if (Objects.equals(texto(antes), texto(despues))) return;
        Map<String, Object> cambios = cambiosDeLaPeticion();
        if (cambios == null) return;
        Map<String, Object> par = new LinkedHashMap<>();
        par.put("antes", antes);
        par.put("despues", despues);
        cambios.put(campo, par);
    }

    public static void anotarCambios(Map<String, Object> antes, Map<String, Object> despues) {
        if (antes == null || despues == null) return;
        for (String clave : despues.keySet()) anotarCambio(clave, antes.get(clave), despues.get(clave));
    }

    public static void anotarResumen(String resumen) {
        RequestAttributes r = RequestContextHolder.getRequestAttributes();
        if (r != null && resumen != null) r.setAttribute(ATRIBUTO_RESUMEN, resumen, RequestAttributes.SCOPE_REQUEST);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cambiosDeLaPeticion() {
        RequestAttributes r = RequestContextHolder.getRequestAttributes();
        if (r == null) return null;
        Object actual = r.getAttribute(ATRIBUTO_CAMBIOS, RequestAttributes.SCOPE_REQUEST);
        if (actual instanceof Map<?, ?> m) return (Map<String, Object>) m;
        Map<String, Object> nuevo = new LinkedHashMap<>();
        r.setAttribute(ATRIBUTO_CAMBIOS, nuevo, RequestAttributes.SCOPE_REQUEST);
        return nuevo;
    }

    private static String texto(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    public void registrar(Entrada e) {
        try {
            String usuario = e.usuario();
            if (e.usuarioId() != null) {
                List<String> nombres = jdbc.queryForList("""
                        SELECT COALESCE(NULLIF(btrim(concat_ws(' ', p.nombre, p.paterno, p.materno)), ''), u.username)
                          FROM usuario u LEFT JOIN persona p ON p.id = u.persona_id WHERE u.id = ?
                        """, String.class, e.usuarioId());
                if (!nombres.isEmpty() && nombres.get(0) != null) usuario = nombres.get(0);
            }
            jdbc.update("""
                    INSERT INTO historial_accion (id_usuario, usuario, rol, modulo, accion, metodo, ruta, id_registro,
                                                  estado_http, ok, ip, detalle)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, e.usuarioId(), corto(usuario, 160), corto(e.rol(), 80), corto(e.modulo(), 60), corto(e.accion(), 300),
                    e.metodo(), corto(e.ruta(), 300), e.registroId(), e.estadoHttp(), e.ok(), corto(e.ip(), 60),
                    e.detalle() == null || e.detalle().isEmpty() ? null : json.writeValueAsString(e.detalle()));
        } catch (Exception ex) {
            log.warn("No se pudo guardar el historial de «{}»: {}", e.accion(), ex.getMessage());
        }
    }

    public String nombreDeActividad(Long id) {
        try {
            List<String> n = jdbc.queryForList("SELECT nombre FROM edicion WHERE id = ?", String.class, id);
            return n.isEmpty() ? null : n.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    public List<Map<String, Object>> listar(Filtros f) {
        StringBuilder sql = new StringBuilder("SELECT * FROM historial_accion WHERE TRUE");
        List<Object> params = new ArrayList<>();
        if (f.usuarioId() != null) { sql.append(" AND id_usuario = ?"); params.add(f.usuarioId()); }
        if (f.modulo() != null && !f.modulo().isBlank()) { sql.append(" AND modulo = ?"); params.add(f.modulo().trim()); }
        if (f.desde() != null) { sql.append(" AND cuando >= ?"); params.add(Timestamp.valueOf(f.desde().atStartOfDay())); }
        if (f.hasta() != null) { sql.append(" AND cuando < ?"); params.add(Timestamp.valueOf(f.hasta().plusDays(1).atStartOfDay())); }
        if (Boolean.TRUE.equals(f.soloErrores())) sql.append(" AND NOT ok");
        if (f.texto() != null && !f.texto().isBlank()) {
            sql.append(" AND (accion ILIKE ? OR usuario ILIKE ? OR detalle ILIKE ?)");
            String q = "%" + f.texto().trim() + "%";
            params.add(q);
            params.add(q);
            params.add(q);
        }
        if (f.antesDe() != null) { sql.append(" AND id < ?"); params.add(f.antesDe()); }
        int limite = f.limite() == null ? 100 : Math.max(1, Math.min(500, f.limite()));
        sql.append(" ORDER BY id DESC LIMIT ").append(limite);
        return jdbc.query(sql.toString(), (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("cuando", rs.getTimestamp("cuando").toLocalDateTime().toString());
            m.put("usuarioId", rs.getObject("id_usuario"));
            m.put("usuario", rs.getString("usuario"));
            m.put("rol", rs.getString("rol"));
            m.put("modulo", rs.getString("modulo"));
            m.put("accion", rs.getString("accion"));
            m.put("metodo", rs.getString("metodo"));
            m.put("ruta", rs.getString("ruta"));
            m.put("registroId", rs.getObject("id_registro"));
            m.put("estadoHttp", rs.getObject("estado_http"));
            m.put("ok", rs.getBoolean("ok"));
            m.put("ip", rs.getString("ip"));
            String detalle = rs.getString("detalle");
            Object leido = null;
            if (detalle != null) {
                try { leido = json.readValue(detalle, Map.class); } catch (Exception ex) { leido = Map.of("texto", detalle); }
            }
            m.put("detalle", leido);
            return m;
        }, params.toArray());
    }

    public Map<String, Object> opciones() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("modulos", jdbc.queryForList("SELECT DISTINCT modulo FROM historial_accion ORDER BY modulo", String.class));
        m.put("usuarios", jdbc.queryForList("""
                SELECT DISTINCT ON (id_usuario) id_usuario AS id, usuario AS nombre FROM historial_accion
                 WHERE id_usuario IS NOT NULL ORDER BY id_usuario, id DESC
                """));
        return m;
    }

    private static String corto(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}

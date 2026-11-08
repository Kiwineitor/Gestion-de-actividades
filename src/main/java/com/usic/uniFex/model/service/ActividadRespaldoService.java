package com.usic.uniFex.model.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActividadRespaldoService {

    public static final int DIAS_DE_RESPALDO = 7;

    private static final Set<String> TABLAS = Set.of("edicion", "edicion_dia", "entrada_tipo", "plano_texto",
            "categoria", "categoria_opcion", "puesto");

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Map<String, List<String>> columnas = new java.util.concurrent.ConcurrentHashMap<>();

    public record Resultado(boolean ok, String mensaje, Long id) {
        static Resultado error(String m) { return new Resultado(false, m, null); }
    }

    public String instantanea(Long edicionId) {
        return jdbc.queryForObject("""
                SELECT json_build_object(
                  'edicion', (SELECT row_to_json(e) FROM edicion e WHERE e.id = ?),
                  'dias', COALESCE((SELECT json_agg(d ORDER BY d.fecha) FROM edicion_dia d WHERE d.id_edicion = ?), '[]'::json),
                  'entradas', COALESCE((SELECT json_agg(t ORDER BY t.orden, t.id) FROM entrada_tipo t
                                         WHERE t.id_edicion = ? AND t."_estado" <> 'X'), '[]'::json),
                  'textos', COALESCE((SELECT json_agg(x ORDER BY x.id) FROM plano_texto x
                                       WHERE x.id_edicion = ? AND (x."_estado" IS NULL OR x."_estado" <> 'X')), '[]'::json),
                  'categorias', COALESCE((SELECT json_agg(json_build_object(
                        'categoria', row_to_json(c),
                        'opciones', COALESCE((SELECT json_agg(o ORDER BY o.orden, o.id) FROM categoria_opcion o
                                               WHERE o.id_categoria = c.id AND (o."_estado" IS NULL OR o."_estado" <> 'X')), '[]'::json),
                        'puestos', COALESCE((SELECT json_agg(p ORDER BY p.id) FROM puesto p
                                              WHERE p.id_categoria = c.id AND (p."_estado" IS NULL OR p."_estado" <> 'X')), '[]'::json)
                      ) ORDER BY c.id)
                      FROM categoria c WHERE c.id_edicion = ? AND (c."_estado" IS NULL OR c."_estado" <> 'X')), '[]'::json)
                )::text
                """, String.class, edicionId, edicionId, edicionId, edicionId, edicionId);
    }

    @Transactional
    public void guardar(Long edicionId, String nombre, Long usuarioId) {
        jdbc.update("""
                INSERT INTO actividad_respaldo (id_edicion_original, nombre, datos, creado_por, vence_en)
                VALUES (?, ?, ?, ?, ?)
                """, edicionId, nombre, instantanea(edicionId), usuarioId, LocalDateTime.now().plusDays(DIAS_DE_RESPALDO));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> papelera() {
        return jdbc.query("""
                SELECT id, nombre, creado_en, vence_en,
                       GREATEST(0, CEIL(EXTRACT(EPOCH FROM (vence_en - now())) / 86400))::int AS dias
                  FROM actividad_respaldo WHERE restaurado_en IS NULL AND vence_en > now() ORDER BY creado_en DESC
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("nombre", rs.getString("nombre"));
            m.put("eliminadaEn", rs.getTimestamp("creado_en").toLocalDateTime().toString());
            m.put("venceEn", rs.getTimestamp("vence_en").toLocalDateTime().toString());
            m.put("diasRestantes", rs.getInt("dias"));
            return m;
        });
    }

    @Transactional
    public Resultado restaurar(Long respaldoId) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT nombre, datos FROM actividad_respaldo
                 WHERE id = ? AND restaurado_en IS NULL AND vence_en > now() FOR UPDATE
                """, respaldoId);
        if (filas.isEmpty()) return Resultado.error("El respaldo ya no está disponible");
        Long nueva = recrear((String) filas.get(0).get("datos"), (String) filas.get(0).get("nombre"));
        jdbc.update("UPDATE actividad_respaldo SET restaurado_en = now(), id_edicion_restaurada = ? WHERE id = ?", nueva, respaldoId);
        HistorialService.anotarResumen((String) filas.get(0).get("nombre"));
        return new Resultado(true, "Actividad restaurada como borrador", nueva);
    }

    @Transactional
    public Resultado duplicar(Long edicionId) {
        List<String> nombre = jdbc.queryForList("SELECT nombre FROM edicion WHERE id = ?", String.class, edicionId);
        if (nombre.isEmpty()) return Resultado.error("La actividad no existe");
        Long nueva = recrear(instantanea(edicionId), nombre.get(0) + " (copia)");
        HistorialService.anotarResumen(nombre.get(0));
        return new Resultado(true, "Copia creada como borrador", nueva);
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgar() {
        int n = jdbc.update("DELETE FROM actividad_respaldo WHERE vence_en <= now() OR restaurado_en < now() - interval '30 days'");
        if (n > 0) log.info("Se purgaron {} respaldo(s) de actividades vencidos", n);
    }

    private Long recrear(String datos, String nombreBase) {
        JsonNode raiz;
        try {
            raiz = json.readTree(datos);
        } catch (Exception e) {
            throw new IllegalStateException("El respaldo está dañado", e);
        }
        Map<String, Object> ed = new HashMap<>();
        ed.put("nombre", nombreLibre(nombreBase));
        ed.put("activa", false);
        ed.put("publica", false);
        ed.put("publicacion_independiente", false);
        ed.put("finalizada", false);
        ed.put("estado_evento", "NORMAL");
        ed.put("estado_motivo", null);
        ed.put("estado_nueva_fecha", null);
        ed.put("estado_cambiado_en", null);
        Long edicion = insertar("edicion", raiz.get("edicion"), ed);

        for (JsonNode d : raiz.path("dias")) insertar("edicion_dia", d, Map.of("id_edicion", edicion));
        for (JsonNode t : raiz.path("entradas")) insertar("entrada_tipo", t, Map.of("id_edicion", edicion));
        for (JsonNode x : raiz.path("textos")) insertar("plano_texto", x, Map.of("id_edicion", edicion));
        for (JsonNode c : raiz.path("categorias")) {
            Long categoria = insertar("categoria", c.get("categoria"), Map.of("id_edicion", edicion));
            for (JsonNode o : c.path("opciones")) insertar("categoria_opcion", o, Map.of("id_categoria", categoria));
            for (JsonNode p : c.path("puestos")) {
                Map<String, Object> cambios = new HashMap<>();
                cambios.put("id_categoria", categoria);
                cambios.put("estado_puesto", "X".equals(p.path("estado_puesto").asText()) ? "X" : "L");
                cambios.put("reservado_por_id_usuario", null);
                cambios.put("reserva_expira", null);
                cambios.put("reserva_token", null);
                cambios.put("reserva_origen", null);
                cambios.put("version", 0);
                insertar("puesto", p, cambios);
            }
        }
        return edicion;
    }

    private String nombreLibre(String base) {
        String nombre = base.length() > 110 ? base.substring(0, 110) : base;
        String candidato = nombre;
        for (int i = 2; existeNombre(candidato) && i < 50; i++) candidato = nombre + " (" + i + ")";
        return candidato;
    }

    private boolean existeNombre(String nombre) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM edicion WHERE lower(nombre) = lower(?)", Integer.class, nombre);
        return n != null && n > 0;
    }

    private Long insertar(String tabla, JsonNode fila, Map<String, Object> cambios) {
        if (!TABLAS.contains(tabla) || fila == null || !fila.isObject()) throw new IllegalArgumentException("Respaldo inválido");
        ObjectNode o = ((ObjectNode) fila).deepCopy();
        o.remove("id");
        cambios.forEach((k, v) -> o.set(k, json.valueToTree(v)));
        List<String> usar = new ArrayList<>();
        for (String c : columnasDe(tabla)) if (!"id".equals(c) && o.has(c)) usar.add("\"" + c + "\"");
        String lista = String.join(", ", usar);
        return jdbc.queryForObject("INSERT INTO " + tabla + " (" + lista + ") SELECT " + lista
                + " FROM json_populate_record(NULL::" + tabla + ", ?::json) RETURNING id", Long.class, o.toString());
    }

    private List<String> columnasDe(String tabla) {
        return columnas.computeIfAbsent(tabla, t -> jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_schema = 'public' AND table_name = ? ORDER BY ordinal_position
                """, String.class, t));
    }
}

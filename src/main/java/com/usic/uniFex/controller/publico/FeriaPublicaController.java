package com.usic.uniFex.controller.publico;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dao.ICategoriaDao;
import com.usic.uniFex.model.dao.IEdicionDao;
import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.entity.Categoria;
import com.usic.uniFex.model.entity.Edicion;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.dto.PlanoDTO;
import com.usic.uniFex.model.service.PlanoTextoService;
import com.usic.uniFex.model.service.VisitasPaginaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/publico/feria")
@RequiredArgsConstructor
public class FeriaPublicaController {

    private final IEdicionDao edicionDao;
    private final ICategoriaDao categoriaDao;
    private final IPuestoDao puestoDao;
    private final com.usic.uniFex.model.service.PlanoService planoService;
    private final VisitasPaginaService visitasService;
    private final PlanoTextoService planoTextos;
    private final JdbcTemplate jdbc;

    @GetMapping("/ediciones")
    public List<Map<String, Object>> edicionesPublicas() {
        return jdbc.query("""
                SELECT e.id, e.nombre, e.anio, e.activa, e.lugar, e.fechas_texto, e.portada_archivo,
                       count(p.id) AS total, count(p.id) FILTER (WHERE p.estado_puesto = 'L') AS libres
                  FROM edicion e
                  LEFT JOIN categoria c ON c.id_edicion = e.id AND c."_estado" IS DISTINCT FROM 'X'
                  LEFT JOIN puesto p ON p.id_categoria = c.id AND p."_estado" IS DISTINCT FROM 'X'
                 WHERE e.publica AND (e.activa OR e.publicacion_independiente) GROUP BY e.id ORDER BY e.activa DESC, e.anio DESC, e.id DESC
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("nombre", rs.getString("nombre"));
            m.put("anio", rs.getInt("anio"));
            m.put("activa", rs.getBoolean("activa"));
            m.put("lugar", rs.getString("lugar"));
            m.put("fechasTexto", rs.getString("fechas_texto"));
            String portada = rs.getString("portada_archivo");
            m.put("portadaUrl", portada == null ? null : "/files/" + portada);
            m.put("total", rs.getLong("total"));
            m.put("libres", rs.getLong("libres"));
            return m;
        });
    }

    private Edicion edicion(Long id) {
        return (id == null ? edicionDao.findFirstByActivaTrueOrderByAnioDesc() : edicionDao.findById(id)).orElse(null);
    }

    @GetMapping
    public Map<String, Object> info(@RequestParam(required = false) Long edicion) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();

        Edicion edicionActiva = edicion(edicion);
        Map<String, Object> publico = edicionActiva == null ? null : datosPublicos(edicionActiva.getId());
        if (publico == null || !Boolean.TRUE.equals(publico.get("publica"))) {
            cuerpo.put("edicion", null);
            return cuerpo;
        }
        if (edicionActiva != null) {
            Map<String, Object> edicionInfo = new LinkedHashMap<>();
            edicionInfo.put("id", edicionActiva.getId());
            edicionInfo.put("nombre", edicionActiva.getNombre());
            edicionInfo.put("anio", edicionActiva.getAnio());
            if (edicionActiva.getPlanoArchivo() != null) {
                Map<String, Object> planoInfo = new LinkedHashMap<>();
                planoInfo.put("archivo", edicionActiva.getPlanoArchivo());
                planoInfo.put("ancho", edicionActiva.getPlanoAncho());
                planoInfo.put("alto", edicionActiva.getPlanoAlto());
                planoInfo.put("version", edicionActiva.getPlanoVersion());
                planoInfo.put("subidoEn", edicionActiva.getPlanoSubidoEn());
                edicionInfo.put("plano", planoInfo);
            } else {
                edicionInfo.put("plano", null);
            }
            edicionInfo.put("urlPlano", edicionActiva.getPlanoArchivo() != null
                    ? "/files/" + edicionActiva.getPlanoArchivo() + "?v=" + edicionActiva.getPlanoVersion()
                    : "/files/mapa.pdf");
            edicionInfo.putAll(publico);
            edicionInfo.put("recibeInteresados", Boolean.TRUE.equals(edicionActiva.getActiva()) && Boolean.TRUE.equals(publico.get("recibeInteresados")));
            cuerpo.put("edicion", edicionInfo);
        } else {
            cuerpo.put("edicion", null);
        }

        cuerpo.put("ediciones", edicionesPublicas());

        List<Categoria> categorias = categoriaDao.deEdicion(edicionActiva.getId());
        List<Map<String, Object>> categoriasInfo = categorias.stream()
                .filter(c -> c.getEstado() == null || !"X".equals(c.getEstado()))
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("nombre", c.getNombre());
                    m.put("descripcion", c.getDescripcion());
                    m.put("color", c.getColor());
                    m.put("forma", c.getForma());
                    m.put("tamanoMapa", c.getTamanoMapa());
                    m.put("precioBase", c.getPrecioBase());
                    return m;
                })
                .toList();
        cuerpo.put("categorias", categoriasInfo);

        List<Puesto> puestosActivos = puestoDao.listarActivosDe(edicionActiva.getId());
        long total = puestosActivos.size();
        long libres = puestosActivos.stream().filter(p -> "L".equals(p.getEstadoPuesto())).count();
        long enTramite = puestosActivos.stream().filter(p -> "T".equals(p.getEstadoPuesto())).count();
        long ocupados = puestosActivos.stream().filter(p -> "O".equals(p.getEstadoPuesto())).count();
        long bloqueados = puestosActivos.stream().filter(p -> "X".equals(p.getEstadoPuesto())).count();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("libres", libres);
        stats.put("enTramite", enTramite);
        stats.put("ocupados", ocupados);
        stats.put("bloqueados", bloqueados);
        stats.put("porcentajeOcupacion", total > 0 ? Math.round((double) ocupados / total * 100) : 0);
        cuerpo.put("estadisticas", stats);

        Map<Long, List<Puesto>> porCategoria = puestosActivos.stream()
                .filter(p -> p.getCategoria() != null)
                .collect(Collectors.groupingBy(p -> p.getCategoria().getId()));

        List<Map<String, Object>> statsPorCategoria = categorias.stream()
                .filter(c -> c.getEstado() == null || !"X".equals(c.getEstado()))
                .map(c -> {
                    List<Puesto> lista = porCategoria.getOrDefault(c.getId(), List.of());
                    long catTotal = lista.size();
                    long catLibres = lista.stream().filter(p -> "L".equals(p.getEstadoPuesto())).count();
                    long catOcupados = lista.stream().filter(p -> "O".equals(p.getEstadoPuesto())).count();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("categoriaId", c.getId());
                    m.put("categoriaNombre", c.getNombre());
                    m.put("color", c.getColor());
                    m.put("forma", c.getForma());
                    m.put("total", catTotal);
                    m.put("libres", catLibres);
                    m.put("ocupados", catOcupados);
                    m.put("porcentajeOcupacion", catTotal > 0 ? Math.round((double) catOcupados / catTotal * 100) : 0);
                    return m;
                })
                .toList();
        cuerpo.put("estadisticasPorCategoria", statsPorCategoria);

        PlanoDTO plano = planoService.deEdicion(edicionActiva.getId());
        if (plano != null) {
            Map<String, Object> planoInfo = new LinkedHashMap<>();
            planoInfo.put("url", plano.url());
            planoInfo.put("ancho", plano.ancho());
            planoInfo.put("alto", plano.alto());
            planoInfo.put("version", plano.version());
            planoInfo.put("propio", plano.propio());
            planoInfo.put("sinFondo", plano.sinFondo());
            cuerpo.put("plano", planoInfo);
        }

        return cuerpo;
    }

    private Map<String, Object> datosPublicos(Long id) {
        return jdbc.queryForObject("""
                SELECT lugar, fechas_texto, descripcion_publica, contacto, portada_archivo, publica, recibe_interesados,
                       (activa OR publicacion_independiente) AS publicable, pago_qr_archivo, pago_banco, pago_cuenta, pago_titular, pago_instrucciones, pago_oficina
                  FROM edicion WHERE id = ?
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lugar", rs.getString("lugar"));
            m.put("fechasTexto", rs.getString("fechas_texto"));
            m.put("descripcion", rs.getString("descripcion_publica"));
            m.put("contacto", rs.getString("contacto"));
            String portada = rs.getString("portada_archivo");
            m.put("portadaUrl", portada == null ? null : "/files/" + portada);
            m.put("publica", rs.getBoolean("publica") && rs.getBoolean("publicable"));
            m.put("recibeInteresados", rs.getBoolean("recibe_interesados"));
            String qr = rs.getString("pago_qr_archivo");
            Map<String, Object> pago = new LinkedHashMap<>();
            pago.put("qrUrl", qr == null ? null : "/files/" + qr);
            pago.put("banco", rs.getString("pago_banco"));
            pago.put("cuenta", rs.getString("pago_cuenta"));
            pago.put("titular", rs.getString("pago_titular"));
            pago.put("instrucciones", rs.getString("pago_instrucciones"));
            pago.put("oficina", rs.getString("pago_oficina"));
            m.put("pago", pago);
            return m;
        }, id);
    }

    @GetMapping("/plano")
    public Map<String, Object> plano(@RequestParam(required = false) Long edicion) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        Edicion activa = edicion(edicion);
        Map<String, Object> publico = activa == null ? null : datosPublicos(activa.getId());
        if (publico == null || !Boolean.TRUE.equals(publico.get("publica"))) {
            cuerpo.put("disponible", false);
            return cuerpo;
        }
        PlanoDTO plano = planoService.deEdicion(activa.getId());
        Map<String, Object> planoInfo = new LinkedHashMap<>();
        planoInfo.put("url", plano.url());
        planoInfo.put("ancho", plano.ancho());
        planoInfo.put("alto", plano.alto());
        planoInfo.put("propio", plano.propio());
        planoInfo.put("sinFondo", plano.sinFondo());
        cuerpo.put("disponible", true);
        cuerpo.put("plano", planoInfo);
        cuerpo.put("puestos", jdbc.query("""
                SELECT p.id, p.codigo, p.tamano, p.estado_puesto, p.mapa_x, p.mapa_y, p.mapa_escala, p.mapa_rotacion,
                       p.referencia, COALESCE(p.precio, c.precio_base, 0) AS precio,
                       c.id AS categoria_id, c.nombre AS categoria, c.color, c.forma, c.tamano_mapa, c.mapa_proporcion,
                       (SELECT max(CASE WHEN i.estado = 'ATENDIDO' AND i.id_inscripcion IS NULL THEN 2 WHEN i.estado = 'NUEVO' THEN 1 ELSE 0 END)
                          FROM interesado_stand i WHERE i.id_puesto = p.id) AS solicitud
                  FROM puesto p JOIN categoria c ON c.id = p.id_categoria
                 WHERE c.id_edicion = ? AND p.mapa_x IS NOT NULL AND p.mapa_y IS NOT NULL
                   AND (p."_estado" IS NULL OR p."_estado" <> 'X') AND (c."_estado" IS NULL OR c."_estado" <> 'X')
                 ORDER BY c.nombre, p.codigo
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            String estado = rs.getString("estado_puesto");
            int solicitud = rs.getInt("solicitud");
            if (Puesto.LIBRE.equals(estado) && solicitud == 2) estado = "R";
            else if (Puesto.LIBRE.equals(estado) && solicitud == 1) estado = "S";
            m.put("id", rs.getLong("id"));
            m.put("codigo", rs.getString("codigo"));
            m.put("tamano", rs.getString("tamano"));
            m.put("estado", estado);
            m.put("mapaX", rs.getDouble("mapa_x"));
            m.put("mapaY", rs.getDouble("mapa_y"));
            m.put("mapaEscala", rs.getObject("mapa_escala") == null ? 1.0 : rs.getDouble("mapa_escala"));
            m.put("mapaRotacion", rs.getInt("mapa_rotacion"));
            m.put("referencia", rs.getString("referencia"));
            m.put("precio", rs.getBigDecimal("precio"));
            m.put("categoriaId", rs.getLong("categoria_id"));
            m.put("categoria", rs.getString("categoria"));
            m.put("color", rs.getString("color"));
            m.put("forma", rs.getString("forma"));
            m.put("tamanoMapa", rs.getObject("tamano_mapa") == null ? 0.012 : rs.getDouble("tamano_mapa"));
            m.put("mapaProporcion", rs.getObject("mapa_proporcion") == null ? 1.0 : rs.getDouble("mapa_proporcion"));
            return m;
        }, activa.getId()));
        cuerpo.put("textos", planoTextos.listar(activa.getId()));
        return cuerpo;
    }

    @PostMapping("/visitas")
    public Map<String, Object> registrarVisita() {
        return Map.of("total", visitasService.registrar(VisitasPaginaService.FERIA_PUBLICA));
    }

    @GetMapping("/visitas")
    public Map<String, Object> visitas() {
        return Map.of("total", visitasService.total(VisitasPaginaService.FERIA_PUBLICA));
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<Puesto> puestosActivos = puestoDao.listarActivos();
        long total = puestosActivos.size();
        long libres = puestosActivos.stream().filter(p -> "L".equals(p.getEstadoPuesto())).count();
        long ocupados = puestosActivos.stream().filter(p -> "O".equals(p.getEstadoPuesto())).count();

        return Map.of(
                "total", total,
                "libres", libres,
                "ocupados", ocupados,
                "enTramite", puestosActivos.stream().filter(p -> "T".equals(p.getEstadoPuesto())).count(),
                "bloqueados", puestosActivos.stream().filter(p -> "X".equals(p.getEstadoPuesto())).count(),
                "porcentajeOcupacion", total > 0 ? Math.round((double) ocupados / total * 100) : 0);
    }
}

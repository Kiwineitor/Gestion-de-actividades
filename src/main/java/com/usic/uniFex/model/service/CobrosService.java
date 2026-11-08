package com.usic.uniFex.model.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dto.ControlVentasDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CobrosService {

    private final JdbcTemplate jdbc;
    private final ControlVentasService control;
    private final EntradaService entradas;
    private final ConfiguracionActividadService configuracion;

    @Transactional(readOnly = true)
    public Map<String, Object> resumen(Long edicion) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT id, nombre, vende_espacios, vende_entradas, meta_recaudacion, tipo_espacio, etiqueta_espacio, finalizada,
                       publica AND (activa OR publicacion_independiente) AS publicada
                  FROM edicion WHERE id = ?
                """, edicion);
        if (filas.isEmpty()) return null;
        Map<String, Object> e = filas.get(0);
        boolean conEspacios = Boolean.TRUE.equals(e.get("vende_espacios"));
        boolean conEntradas = Boolean.TRUE.equals(e.get("vende_entradas"));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("edicion", edicion);
        r.put("nombre", e.get("nombre"));
        r.put("tipoEspacio", e.get("tipo_espacio"));
        r.put("etiquetaEspacio", e.get("etiqueta_espacio"));
        r.put("vendeEspacios", conEspacios);
        r.put("vendeEntradas", conEntradas);
        r.put("publicada", e.get("publicada"));
        r.put("finalizada", e.get("finalizada"));

        BigDecimal vendido = BigDecimal.ZERO;
        BigDecimal recaudado = BigDecimal.ZERO;
        BigDecimal porCobrar = BigDecimal.ZERO;
        Map<LocalDate, BigDecimal> porDia = new TreeMap<>();
        Map<String, BigDecimal> porMedio = new LinkedHashMap<>();
        Map<String, Map<String, Object>> porVendedor = new LinkedHashMap<>();

        if (conEspacios) {
            ControlVentasService.Calculo calculo = control.calcular(edicion);
            List<ControlVentasDTO.Venta> ventas = control.filtrar(calculo, null);
            ControlVentasDTO.Resumen res = control.resumen(ventas);
            vendido = vendido.add(res.totalVendido());
            recaudado = recaudado.add(res.totalRecaudado());
            porCobrar = porCobrar.add(res.porRendir());
            Map<String, Object> espacios = new LinkedHashMap<>();
            espacios.put("operaciones", res.operaciones());
            espacios.put("vendidos", res.puestos());
            espacios.put("vendido", res.totalVendido());
            espacios.put("recaudado", res.totalRecaudado());
            espacios.put("porCobrar", res.porRendir());
            espacios.put("ventasPorCobrar", res.ventasPorRendir() + res.ventasParciales());
            espacios.put("categorias", control.categorias(calculo, ventas));
            Map<String, Object> inventario = jdbc.queryForMap("""
                    SELECT count(*) FILTER (WHERE p.estado_puesto <> 'X') AS total,
                           count(*) FILTER (WHERE p.estado_puesto = 'L') AS libres,
                           count(*) FILTER (WHERE p.estado_puesto = 'T') AS en_proceso,
                           COALESCE(sum(COALESCE(p.precio, c.precio_base, 0)) FILTER (WHERE p.estado_puesto IN ('L', 'T')), 0) AS por_vender
                      FROM puesto p JOIN categoria c ON c.id = p.id_categoria
                     WHERE c.id_edicion = ? AND p."_estado" IS DISTINCT FROM 'X' AND c."_estado" IS DISTINCT FROM 'X'
                    """, edicion);
            espacios.put("total", inventario.get("total"));
            espacios.put("libres", inventario.get("libres"));
            espacios.put("enProceso", inventario.get("en_proceso"));
            espacios.put("porVender", inventario.get("por_vender"));
            r.put("espacios", espacios);
            for (ControlVentasDTO.Venta v : ventas) {
                if (v.getFecha() != null) porDia.merge(v.getFecha().toLocalDate(), v.getTotalVendido(), BigDecimal::add);
                if (v.getTotalRecaudado().signum() > 0) {
                    porMedio.merge(v.getFormaPago() == null ? "Comprobante" : v.getFormaPago(), v.getTotalRecaudado(), BigDecimal::add);
                }
                sumarVendedor(porVendedor, v.getVendedor() == null ? "Página pública" : v.getVendedor(), v.getTotalVendido(), 1);
            }
        }

        if (conEntradas) {
            Map<String, Object> resEntradas = entradas.resumen(edicion);
            BigDecimal recaudadoEntradas = (BigDecimal) resEntradas.get("recaudado");
            vendido = vendido.add(recaudadoEntradas);
            recaudado = recaudado.add(recaudadoEntradas);
            BigDecimal porVender = BigDecimal.ZERO;
            for (Object o : (List<?>) resEntradas.get("tipos")) {
                Map<?, ?> t = (Map<?, ?>) o;
                if (t.get("quedan") != null) {
                    porVender = porVender.add(((BigDecimal) t.get("precio")).multiply(BigDecimal.valueOf(((Number) t.get("quedan")).longValue())));
                }
            }
            resEntradas.put("porVender", porVender);
            r.put("entradas", resEntradas);
            jdbc.query("""
                    SELECT CAST(COALESCE(c.validado_en, c.creado_en) AS date) AS dia, c.modo_pago, c.total,
                           COALESCE(u.username, 'Página pública') AS vendedor
                      FROM entrada_compra c LEFT JOIN usuario u ON u.id = c.registrado_por
                     WHERE c.id_edicion = ? AND c.estado = 'APROBADA'
                    """, rs -> {
                BigDecimal total = rs.getBigDecimal("total");
                Date dia = rs.getDate("dia");
                if (dia != null) porDia.merge(dia.toLocalDate(), total, BigDecimal::add);
                if (total.signum() > 0) porMedio.merge(medio(rs.getString("modo_pago")), total, BigDecimal::add);
                sumarVendedor(porVendedor, rs.getString("vendedor"), total, 1);
            }, edicion);
        }

        Map<String, Object> enRevision = jdbc.queryForMap("""
                SELECT (SELECT count(*) FROM interesado_stand i JOIN categoria c ON c.id = i.id_categoria
                         WHERE c.id_edicion = ? AND i.estado = 'NUEVO') AS solicitudes,
                       (SELECT COALESCE(sum(COALESCE(p.precio, c.precio_base, 0)), 0) FROM interesado_stand i
                          JOIN categoria c ON c.id = i.id_categoria JOIN puesto p ON p.id = i.id_puesto
                         WHERE c.id_edicion = ? AND i.estado = 'NUEVO') AS solicitudes_monto,
                       (SELECT count(*) FROM entrada_compra WHERE id_edicion = ? AND estado = 'REVISION') AS compras,
                       (SELECT COALESCE(sum(total), 0) FROM entrada_compra WHERE id_edicion = ? AND estado = 'REVISION') AS compras_monto
                """, edicion, edicion, edicion, edicion);
        long pendientes = ((Number) enRevision.get("solicitudes")).longValue() + ((Number) enRevision.get("compras")).longValue();
        BigDecimal montoRevision = ((BigDecimal) enRevision.get("solicitudes_monto")).add((BigDecimal) enRevision.get("compras_monto"));

        Map<String, Object> meta = configuracion.metaCalculada(edicion);
        BigDecimal metaFinal = e.get("meta_recaudacion") != null ? (BigDecimal) e.get("meta_recaudacion") : (BigDecimal) meta.get("total");
        r.put("meta", metaFinal);
        r.put("metaAutomatica", e.get("meta_recaudacion") == null);
        r.put("vendido", vendido);
        r.put("recaudado", recaudado);
        r.put("porCobrar", porCobrar);
        r.put("porValidar", pendientes);
        r.put("porValidarMonto", montoRevision);
        r.put("porVender", BigDecimal.ZERO
                .add(conEspacios ? (BigDecimal) ((Map<?, ?>) r.get("espacios")).get("porVender") : BigDecimal.ZERO)
                .add(conEntradas ? (BigDecimal) ((Map<?, ?>) r.get("entradas")).get("porVender") : BigDecimal.ZERO));
        r.put("avance", metaFinal.signum() > 0
                ? recaudado.multiply(BigDecimal.valueOf(100)).divide(metaFinal, 1, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        List<Map<String, Object>> dias = new ArrayList<>();
        BigDecimal acumulado = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, BigDecimal> d : porDia.entrySet()) {
            acumulado = acumulado.add(d.getValue());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fecha", d.getKey().toString());
            m.put("monto", d.getValue());
            m.put("acumulado", acumulado);
            dias.add(m);
        }
        r.put("porDia", dias);
        List<Map<String, Object>> medios = new ArrayList<>();
        porMedio.forEach((k, v) -> medios.add(Map.of("medio", k, "monto", v)));
        medios.sort((a, b) -> ((BigDecimal) b.get("monto")).compareTo((BigDecimal) a.get("monto")));
        r.put("porMedio", medios);
        List<Map<String, Object>> vendedores = new ArrayList<>(porVendedor.values());
        vendedores.sort((a, b) -> ((BigDecimal) b.get("monto")).compareTo((BigDecimal) a.get("monto")));
        r.put("porVendedor", vendedores);
        return r;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> ventas(Long edicion) {
        List<Map<String, Object>> lista = new ArrayList<>();
        Boolean conEspacios = jdbc.queryForObject("SELECT vende_espacios FROM edicion WHERE id = ?", Boolean.class, edicion);
        if (Boolean.TRUE.equals(conEspacios)) {
            ControlVentasService.Calculo calculo = control.calcular(edicion);
            for (ControlVentasDTO.Venta v : control.filtrar(calculo, null)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tipo", "ESPACIO");
                m.put("id", v.getInscripcionId());
                m.put("codigo", v.getCodigoVenta());
                m.put("fecha", v.getFecha() == null ? null : v.getFecha().toString());
                m.put("comprador", v.getEntidad());
                m.put("contacto", v.getRepresentante());
                m.put("celular", v.getCelularRepresentante());
                m.put("detalle", (v.getCategorias() == null ? "" : v.getCategorias()) + (v.getPuestos() == null ? "" : " · " + v.getPuestos()));
                m.put("total", v.getTotalVendido());
                m.put("recaudado", v.getTotalRecaudado());
                m.put("porCobrar", v.getPorRendir());
                m.put("estadoPago", v.getEstadoPago());
                m.put("medio", v.getFormaPago());
                m.put("vendedor", v.getVendedor() == null ? "Página pública" : v.getVendedor());
                m.put("canal", v.getCanal());
                m.put("atendio", v.getVendedor());
                m.put("comprobanteUrl", v.getComprobanteUrl());
                lista.add(m);
            }
        }
        for (Map<String, Object> c : entradas.listar(edicion, "APROBADA")) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tipo", "ENTRADA");
            m.put("id", c.get("id"));
            m.put("codigo", c.get("codigo"));
            m.put("fecha", c.get("validadoEn") != null ? c.get("validadoEn") : c.get("creadoEn"));
            m.put("comprador", c.get("nombre"));
            m.put("contacto", c.get("ci") == null ? null : "C.I. " + c.get("ci"));
            m.put("celular", c.get("celular"));
            m.put("celularExtra", c.get("celularExtra"));
            m.put("cantidad", c.get("cantidad"));
            m.put("detalle", c.get("resumen"));
            m.put("total", c.get("total"));
            m.put("recaudado", c.get("total"));
            m.put("porCobrar", BigDecimal.ZERO);
            m.put("estadoPago", ((BigDecimal) c.get("total")).signum() == 0 ? ControlVentasDTO.SIN_COSTO : ControlVentasDTO.PAGADO);
            m.put("medio", medio(String.valueOf(c.get("modoPago"))));
            m.put("vendedor", c.get("vendedor") == null ? "Página pública" : c.get("vendedor"));
            boolean enLinea = c.get("vendedor") == null;
            m.put("canal", enLinea ? "EN_LINEA" : "EN_PERSONA");
            m.put("atendio", enLinea ? c.get("validador") : c.get("vendedor"));
            m.put("comprobanteUrl", c.get("comprobanteUrl"));
            lista.add(m);
        }
        lista.sort((a, b) -> String.valueOf(b.get("fecha")).compareTo(String.valueOf(a.get("fecha"))));
        return lista;
    }

    private static void sumarVendedor(Map<String, Map<String, Object>> mapa, String quien, BigDecimal monto, int operaciones) {
        Map<String, Object> v = mapa.computeIfAbsent(quien, k -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("vendedor", k);
            m.put("operaciones", 0);
            m.put("monto", BigDecimal.ZERO);
            return m;
        });
        v.put("operaciones", (Integer) v.get("operaciones") + operaciones);
        v.put("monto", ((BigDecimal) v.get("monto")).add(monto == null ? BigDecimal.ZERO : monto));
    }

    private static String medio(String modo) {
        if (modo == null) return "Otro";
        return switch (modo) {
            case "EFECTIVO" -> "Efectivo";
            case "QR" -> "QR en punto de venta";
            case "COMPROBANTE" -> "Depósito / transferencia";
            case "OFICINA" -> "En oficina";
            case "GRATIS" -> "Sin costo";
            default -> "Otro";
        };
    }
}

package com.usic.uniFex.model.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.uniFex.model.dto.AnalisisDTO;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class AnalisisVentasService {

    @PersistenceContext
    private EntityManager em;

    private static final String EDICION_ACTIVA =
            "(select ed.id from edicion ed where ed.activa limit 1)";

    private static final String DESDE_VENTAS = """
              from inscripcion i
              join inscripcion_puesto ip on ip.id_inscripcion = i.id and ip.id_puesto is not null
                   and (ip."_estado" is null or ip."_estado" <> 'X')
              join puesto p on p.id = ip.id_puesto
            """;

    private static final String SOLO_VIVAS_EDICION_ACTIVA = """
             where (i."_estado" is null or i."_estado" <> 'X')
               and i.id_edicion = """ + EDICION_ACTIVA;

    @Transactional(readOnly = true)
    public List<AnalisisDTO.Ocupacion> ocupacion() {
        String sql = """
                with vendido as (
                    select p.id_categoria, sum(ip.costo) as bs
                      from inscripcion i
                      join inscripcion_puesto ip on ip.id_inscripcion = i.id and ip.id_puesto is not null
                           and (ip."_estado" is null or ip."_estado" <> 'X')
                      join puesto p on p.id = ip.id_puesto
                     where (i."_estado" is null or i."_estado" <> 'X')
                       and i.id_edicion = %s
                     group by p.id_categoria
                )
                select c.nombre,
                       count(*) filter (where p.estado_puesto = 'O')::int as vendidas,
                       count(*) filter (where p.estado_puesto = 'T')::int as tramite,
                       count(*) filter (where p.estado_puesto = 'L')::int as libres,
                       count(*) filter (where p.estado_puesto = 'X')::int as bloqueadas,
                       count(*)::int as total,
                       coalesce(v.bs, 0) as bs_vendido,
                       coalesce(sum(coalesce(p.precio, c.precio_base, 0))
                                filter (where p.estado_puesto in ('L', 'T')), 0) as bs_por_vender
                  from puesto p
                  join categoria c on c.id = p.id_categoria
                  left join vendido v on v.id_categoria = c.id
                 where (p."_estado" is null or p."_estado" <> 'X')
                   and (c."_estado" is null or c."_estado" <> 'X')
                   and c.id_edicion = (select ed.id from edicion ed where ed.activa limit 1)
                 group by c.id, c.nombre, v.bs
                 order by c.nombre
                """.formatted(EDICION_ACTIVA);

        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery(sql).getResultList();
        List<AnalisisDTO.Ocupacion> r = new ArrayList<>();
        for (Object[] f : filas) {
            int vendidas = entero(f[1]);
            int tramite = entero(f[2]);
            int libres = entero(f[3]);
            int bloqueadas = entero(f[4]);
            int total = entero(f[5]);
            int vendible = total - bloqueadas;
            BigDecimal pct = vendible <= 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(vendidas * 100.0 / vendible).setScale(1, RoundingMode.HALF_UP);
            r.add(new AnalisisDTO.Ocupacion(texto(f[0]), vendidas, tramite, libres, bloqueadas,
                    total, pct, decimal(f[6]), decimal(f[7])));
        }
        return r;
    }

    @Transactional(readOnly = true)
    public List<AnalisisDTO.Vendedor> vendedores() {
        String sql = """
                select u.id,
                       coalesce(nullif(btrim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)), ''), u.username),
                       a.sigla, ca.nombre,
                       count(distinct i.id)::int as ventas,
                       count(ip.id)::int as casetas,
                       coalesce(sum(ip.costo), 0) as total_bs
                """ + DESDE_VENTAS + """
                  join usuario u on u.id = i."_registro_id_usuario"
                  left join persona pe on pe.id = u.persona_id
                  left join carrera ca on ca.id = pe.id_carrera
                  left join area a on a.id = ca.id_area
                """ + SOLO_VIVAS_EDICION_ACTIVA + """
                 group by u.id, pe.nombre, pe.paterno, pe.materno, u.username, a.sigla, ca.nombre
                 order by total_bs desc, casetas desc
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery(sql).getResultList();
        List<AnalisisDTO.Vendedor> r = new ArrayList<>();
        for (Object[] f : filas) {
            int ventas = entero(f[4]);
            BigDecimal total = decimal(f[6]);
            BigDecimal medio = ventas == 0 ? BigDecimal.ZERO
                    : total.divide(BigDecimal.valueOf(ventas), 2, RoundingMode.HALF_UP);
            r.add(new AnalisisDTO.Vendedor(numero(f[0]), texto(f[1]), texto(f[2]), texto(f[3]),
                    ventas, entero(f[5]), total, medio));
        }
        return r;
    }

    @Transactional(readOnly = true)
    public List<AnalisisDTO.Facultad> porFacultad() {
        String sql = """
                select a.sigla, a.nombre,
                       count(distinct u.id)::int as vendedores,
                       count(distinct i.id)::int as ventas,
                       count(ip.id)::int as casetas,
                       coalesce(sum(ip.costo), 0) as total_bs
                """ + DESDE_VENTAS + """
                  join usuario u on u.id = i."_registro_id_usuario"
                  left join persona pe on pe.id = u.persona_id
                  left join carrera ca on ca.id = pe.id_carrera
                  left join area a on a.id = ca.id_area
                """ + SOLO_VIVAS_EDICION_ACTIVA + """
                 group by a.sigla, a.nombre
                 order by total_bs desc
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery(sql).getResultList();

        BigDecimal total = filas.stream()
                .map(f -> decimal(f[5]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AnalisisDTO.Facultad> r = new ArrayList<>();
        for (Object[] f : filas) {
            BigDecimal bs = decimal(f[5]);
            BigDecimal pct = total.signum() == 0 ? BigDecimal.ZERO
                    : bs.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
            String sigla = texto(f[0]);
            r.add(new AnalisisDTO.Facultad(
                    sigla == null ? "SIN CARRERA" : sigla,
                    nombreArea(sigla, texto(f[1])),
                    entero(f[2]), entero(f[3]), entero(f[4]), bs, pct));
        }
        return r;
    }

    private static String nombreArea(String sigla, String nombre) {
        if (sigla == null) return "Sin carrera asignada";
        if (nombre == null || nombre.equalsIgnoreCase(sigla)) return null;
        return nombre;
    }

    @Transactional(readOnly = true)
    public List<AnalisisDTO.VendidoCategoria> vendidoPorCategoria() {
        List<AnalisisDTO.Ocupacion> base = ocupacion();
        BigDecimal total = base.stream()
                .map(AnalisisDTO.Ocupacion::bsVendido)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return base.stream()
                .sorted((a, b) -> b.bsVendido().compareTo(a.bsVendido()))
                .map(o -> new AnalisisDTO.VendidoCategoria(
                        o.categoria(), o.vendidas(), o.total(), o.porcentajeVendido(), o.bsVendido(),
                        total.signum() == 0 ? BigDecimal.ZERO
                                : o.bsVendido().multiply(BigDecimal.valueOf(100))
                                        .divide(total, 1, RoundingMode.HALF_UP)))
                .toList();
    }

    @Transactional(readOnly = true)
    public AnalisisDTO.Cobros cobros() {
        String cortes = """
                select case
                         when (i.img_comprobante is not null and i.img_comprobante <> '') then 'Con comprobante'
                         when i.pago_contado then 'Contado, SIN comprobante'
                         else 'Sin comprobante'
                       end as concepto,
                       count(distinct i.id)::int, count(ip.id)::int, coalesce(sum(ip.costo), 0)
                """ + DESDE_VENTAS + SOLO_VIVAS_EDICION_ACTIVA + """
                 group by 1
                 order by 1
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> filasCortes = em.createNativeQuery(cortes).getResultList();
        List<AnalisisDTO.Cobro> lista = new ArrayList<>();
        BigDecimal cobrado = BigDecimal.ZERO;
        BigDecimal pendiente = BigDecimal.ZERO;
        for (Object[] f : filasCortes) {
            AnalisisDTO.Cobro c = new AnalisisDTO.Cobro(texto(f[0]), entero(f[1]), entero(f[2]), decimal(f[3]));
            lista.add(c);
            if ("Con comprobante".equals(c.concepto())) cobrado = cobrado.add(c.totalBs());
            else pendiente = pendiente.add(c.totalBs());
        }

        String detalle = """
                select i.id, e.nombre,
                       coalesce(nullif(btrim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)), ''), u.username),
                       i.pago_contado, coalesce(sum(ip.costo), 0), date(i.fecha_compra),
                       greatest(0, date_part('day', now() - i.fecha_compra))::int
                """ + DESDE_VENTAS + """
                  join entidad e on e.id = i.id_entidad
                  left join usuario u on u.id = i."_registro_id_usuario"
                  left join persona pe on pe.id = u.persona_id
                """ + SOLO_VIVAS_EDICION_ACTIVA + """
                   and (i.img_comprobante is null or i.img_comprobante = '')
                 group by i.id, e.nombre, pe.nombre, pe.paterno, pe.materno, u.username,
                          i.pago_contado, i.fecha_compra
                 order by i.fecha_compra asc nulls last
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> filasDetalle = em.createNativeQuery(detalle).getResultList();
        List<AnalisisDTO.Pendiente> pendientes = new ArrayList<>();
        for (Object[] f : filasDetalle) {
            pendientes.add(new AnalisisDTO.Pendiente(numero(f[0]), texto(f[1]), texto(f[2]),
                    Boolean.TRUE.equals(f[3]), decimal(f[4]), fecha(f[5]), entero(f[6])));
        }
        return new AnalisisDTO.Cobros(lista, pendientes, cobrado, pendiente);
    }

    @Transactional(readOnly = true)
    public List<AnalisisDTO.Dia> avance() {
        String sql = """
                select date(i.fecha_compra) as dia,
                       count(distinct i.id)::int, count(ip.id)::int, coalesce(sum(ip.costo), 0)
                """ + DESDE_VENTAS + SOLO_VIVAS_EDICION_ACTIVA + """
                   and i.fecha_compra is not null
                 group by 1
                 order by 1
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> filas = em.createNativeQuery(sql).getResultList();
        List<AnalisisDTO.Dia> r = new ArrayList<>();
        BigDecimal acumBs = BigDecimal.ZERO;
        int acumCasetas = 0;
        for (Object[] f : filas) {
            BigDecimal bs = decimal(f[3]);
            int casetas = entero(f[2]);
            acumBs = acumBs.add(bs);
            acumCasetas += casetas;
            r.add(new AnalisisDTO.Dia(fecha(f[0]), entero(f[1]), casetas, bs, acumBs, acumCasetas));
        }
        return r;
    }

    private static int entero(Object v) {
        return v instanceof Number n ? n.intValue() : 0;
    }

    private static Long numero(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }

    private static BigDecimal decimal(Object v) {
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private static String texto(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static LocalDate fecha(Object v) {
        if (v instanceof Date d) return d.toLocalDate();
        if (v instanceof Timestamp t) return t.toLocalDateTime().toLocalDate();
        if (v instanceof LocalDate l) return l;
        return null;
    }
}

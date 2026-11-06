package com.usic.uniFex.model.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfiguracionActividadService {

    public static final Set<String> TIPOS_ESPACIO = Set.of("MESA", "PUESTO", "STAND", "CASETA", "CARPA", "OTRO");
    public static final Set<String> COMPRADORES = Set.of("PERSONA", "NEGOCIO");
    public static final int PASO_COMPLETO = 99;
    private static final Locale ES = Locale.forLanguageTag("es-BO");

    private final JdbcTemplate jdbc;
    private final ActividadService actividades;
    private final AvisoPublicoService avisoPublico;

    public record Dia(String fecha, String horaInicio, String horaFin) {
    }

    public record Entrada(Long id, String nombre, String descripcion, BigDecimal precio, Integer cupo) {
    }

    public record Cambios(Integer paso,
                          String nombre, String tipo, String descripcion, String lugar, String contacto, List<Dia> dias,
                          Boolean vendeEntradas, Boolean vendeEspacios, String tipoEspacio, String etiquetaEspacio,
                          String comprador, List<Entrada> entradas,
                          String condiciones, Integer minutosBloqueo, String ventaHasta,
                          String pagoBanco, String pagoCuenta, String pagoTitular, String pagoInstrucciones, String pagoOficina,
                          Boolean usaCredenciales, Boolean credencialEntrada, Boolean credencialRepresentante,
                          Boolean credencialAcompanantes, BigDecimal metaRecaudacion, Boolean metaAutomatica) {
    }

    public record Resultado(boolean ok, String mensaje, Long id) {
        static Resultado error(String m) { return new Resultado(false, m, null); }
    }

    @Transactional
    public Resultado crear(Cambios c, Long usuarioId) {
        String nombre = limpio(c == null ? null : c.nombre(), 120);
        if (nombre == null) return Resultado.error("Escribe el nombre de la actividad");
        String error = validarDias(c.dias());
        if (error != null) return Resultado.error(error);
        Integer anio = c.dias() == null || c.dias().isEmpty() ? null : LocalDate.parse(c.dias().get(0).fecha()).getYear();
        ActividadService.Resultado r = actividades.crear(new ActividadService.Datos(nombre, anio, limpio(c.tipo(), 40),
                null, false, false, null, null, null, null, false, true,
                null, null, null, null, null, null), usuarioId);
        if (!r.ok()) return Resultado.error(r.mensaje());
        jdbc.update("UPDATE edicion SET configuracion_paso = 1 WHERE id = ?", r.id());
        Resultado g = guardar(r.id(), c, usuarioId);
        if (!g.ok()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return g;
        }
        log.info("Actividad {} creada desde el asistente por el usuario {}", r.id(), usuarioId);
        return new Resultado(true, "Actividad creada como borrador", r.id());
    }

    @Transactional
    public Resultado guardar(Long id, Cambios c, Long usuarioId) {
        if (c == null) return Resultado.error("No hay cambios que guardar");
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT id FROM edicion WHERE id = ? FOR UPDATE", id);
        if (filas.isEmpty()) return Resultado.error("La actividad no existe");

        if (c.nombre() != null) {
            String nombre = limpio(c.nombre(), 120);
            if (nombre == null) return Resultado.error("Escribe el nombre de la actividad");
            Integer repetido = jdbc.queryForObject(
                    "SELECT count(*) FROM edicion WHERE lower(nombre) = lower(?) AND id <> ?", Integer.class, nombre, id);
            if (repetido != null && repetido > 0) return Resultado.error("Ya existe una actividad con ese nombre");
            jdbc.update("UPDATE edicion SET nombre = ? WHERE id = ?", nombre, id);
        }
        if (c.tipo() != null) jdbc.update("UPDATE edicion SET tipo = ? WHERE id = ?", limpio(c.tipo(), 40), id);
        if (c.descripcion() != null) jdbc.update("UPDATE edicion SET descripcion_publica = ? WHERE id = ?", texto(c.descripcion(), 3000), id);
        if (c.lugar() != null) jdbc.update("UPDATE edicion SET lugar = ? WHERE id = ?", limpio(c.lugar(), 200), id);
        if (c.contacto() != null) {
            String contacto = limpio(c.contacto(), 120);
            if (contacto != null && contacto.replaceAll("\\D", "").length() < 7) {
                return Resultado.error("El celular de contacto debe tener al menos 7 dígitos");
            }
            jdbc.update("UPDATE edicion SET contacto = ? WHERE id = ?", contacto, id);
        }
        if (c.dias() != null) {
            String error = validarDias(c.dias());
            if (error != null) return Resultado.error(error);
            guardarDias(id, c.dias());
        }

        if (c.tipoEspacio() != null) {
            String tipo = c.tipoEspacio().trim().toUpperCase(Locale.ROOT);
            if (!TIPOS_ESPACIO.contains(tipo)) return Resultado.error("Tipo de espacio no válido");
            jdbc.update("UPDATE edicion SET tipo_espacio = ? WHERE id = ?", tipo, id);
        }
        if (c.etiquetaEspacio() != null) jdbc.update("UPDATE edicion SET etiqueta_espacio = ? WHERE id = ?", limpio(c.etiquetaEspacio(), 40), id);
        if (c.comprador() != null) {
            String comprador = c.comprador().trim().toUpperCase(Locale.ROOT);
            if (!COMPRADORES.contains(comprador)) return Resultado.error("Elige quién compra: una persona o un negocio");
            jdbc.update("UPDATE edicion SET comprador = ? WHERE id = ?", comprador, id);
        }
        if (c.vendeEntradas() != null) jdbc.update("UPDATE edicion SET vende_entradas = ? WHERE id = ?", c.vendeEntradas(), id);
        if (c.vendeEspacios() != null) jdbc.update("UPDATE edicion SET vende_espacios = ? WHERE id = ?", c.vendeEspacios(), id);
        if (c.entradas() != null) {
            String error = guardarEntradas(id, c.entradas(), usuarioId);
            if (error != null) return Resultado.error(error);
        }
        Boolean vendeEntradas = jdbc.queryForObject("SELECT vende_entradas FROM edicion WHERE id = ?", Boolean.class, id);
        Boolean vendeEspacios = jdbc.queryForObject("SELECT vende_espacios FROM edicion WHERE id = ?", Boolean.class, id);
        if (c.vendeEntradas() != null || c.vendeEspacios() != null) {
            if (!Boolean.TRUE.equals(vendeEntradas) && !Boolean.TRUE.equals(vendeEspacios)) {
                return Resultado.error("Elige qué vas a vender: entradas, espacios o ambos");
            }
            if (Boolean.TRUE.equals(vendeEntradas) && contarEntradas(id) == 0) {
                return Resultado.error("Agrega al menos un tipo de entrada con su precio");
            }
        }

        if (c.condiciones() != null) jdbc.update("UPDATE edicion SET condiciones = ? WHERE id = ?", texto(c.condiciones(), 5000), id);
        if (c.minutosBloqueo() != null) {
            if (c.minutosBloqueo() < 5 || c.minutosBloqueo() > 120) return Resultado.error("El tiempo para pagar va de 5 a 120 minutos");
            jdbc.update("UPDATE edicion SET minutos_bloqueo = ? WHERE id = ?", c.minutosBloqueo(), id);
        }
        if (c.ventaHasta() != null) {
            LocalDateTime hasta = null;
            if (!c.ventaHasta().isBlank()) {
                try {
                    hasta = LocalDateTime.parse(c.ventaHasta().length() == 16 ? c.ventaHasta() + ":00" : c.ventaHasta());
                } catch (DateTimeParseException e) {
                    return Resultado.error("La fecha límite de venta no es válida");
                }
            }
            jdbc.update("UPDATE edicion SET venta_hasta = ? WHERE id = ?", hasta, id);
        }

        if (c.pagoBanco() != null) jdbc.update("UPDATE edicion SET pago_banco = ? WHERE id = ?", limpio(c.pagoBanco(), 80), id);
        if (c.pagoCuenta() != null) jdbc.update("UPDATE edicion SET pago_cuenta = ? WHERE id = ?", limpio(c.pagoCuenta(), 60), id);
        if (c.pagoTitular() != null) jdbc.update("UPDATE edicion SET pago_titular = ? WHERE id = ?", limpio(c.pagoTitular(), 120), id);
        if (c.pagoInstrucciones() != null) jdbc.update("UPDATE edicion SET pago_instrucciones = ? WHERE id = ?", texto(c.pagoInstrucciones(), 500), id);
        if (c.pagoOficina() != null) jdbc.update("UPDATE edicion SET pago_oficina = ? WHERE id = ?", texto(c.pagoOficina(), 300), id);

        if (c.usaCredenciales() != null) jdbc.update("UPDATE edicion SET usa_credenciales = ? WHERE id = ?", c.usaCredenciales(), id);
        if (c.credencialEntrada() != null) jdbc.update("UPDATE edicion SET credencial_entrada = ? WHERE id = ?", c.credencialEntrada(), id);
        if (c.credencialRepresentante() != null) jdbc.update("UPDATE edicion SET credencial_representante = ? WHERE id = ?", c.credencialRepresentante(), id);
        if (c.credencialAcompanantes() != null) jdbc.update("UPDATE edicion SET credencial_acompanantes = ? WHERE id = ?", c.credencialAcompanantes(), id);

        if (Boolean.TRUE.equals(c.metaAutomatica())) {
            jdbc.update("UPDATE edicion SET meta_recaudacion = NULL WHERE id = ?", id);
        } else if (c.metaRecaudacion() != null) {
            if (c.metaRecaudacion().signum() < 0) return Resultado.error("La meta no puede ser negativa");
            jdbc.update("UPDATE edicion SET meta_recaudacion = ? WHERE id = ?", c.metaRecaudacion(), id);
        }

        if (c.paso() != null && c.paso() > 0) {
            jdbc.update("UPDATE edicion SET configuracion_paso = GREATEST(configuracion_paso, ?) WHERE id = ?",
                    Math.min(c.paso(), PASO_COMPLETO), id);
        }
        avisoPublico.cambio("actividad", id);
        return new Resultado(true, "Cambios guardados", id);
    }

    @Transactional
    public Resultado publicar(Long id, boolean publicar) {
        if (publicar) {
            List<String> faltan = obligatoriosPendientes(id);
            if (faltan == null) return Resultado.error("La actividad no existe");
            if (!faltan.isEmpty()) return Resultado.error("Antes de publicar falta: " + String.join("; ", faltan) + ".");
        }
        ActividadService.Resultado r = actividades.habilitar(id, publicar);
        if (r.ok() && publicar) {
            jdbc.update("UPDATE edicion SET configuracion_paso = ?, finalizada = FALSE WHERE id = ?", PASO_COMPLETO, id);
        }
        return new Resultado(r.ok(), r.mensaje(), r.id());
    }

    @Transactional
    public Resultado finalizar(Long id, boolean finalizada) {
        int n = jdbc.update("UPDATE edicion SET finalizada = ? WHERE id = ?", finalizada, id);
        if (n == 0) return Resultado.error("La actividad no existe");
        avisoPublico.cambio("actividad", id);
        return new Resultado(true, finalizada
                ? "Actividad finalizada: ya no recibe ventas. Sus reportes siguen disponibles."
                : "Actividad reabierta: vuelve a recibir ventas.", id);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> obtener(Long id) {
        List<Map<String, Object>> filas = jdbc.queryForList("""
                SELECT id, nombre, anio, tipo, activa, publica, publicacion_independiente, lugar, fechas_texto,
                       descripcion_publica, contacto, portada_archivo, pago_qr_archivo, pago_banco, pago_cuenta,
                       pago_titular, pago_instrucciones, pago_oficina, tipo_espacio, etiqueta_espacio, comprador,
                       vende_espacios, vende_entradas, condiciones, minutos_bloqueo, venta_hasta, meta_recaudacion,
                       finalizada, usa_credenciales, credencial_entrada, credencial_representante,
                       credencial_acompanantes, configuracion_paso, plano_archivo IS NOT NULL AS con_plano
                  FROM edicion WHERE id = ?
                """, id);
        if (filas.isEmpty()) return null;
        Map<String, Object> f = filas.get(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("nombre", f.get("nombre"));
        m.put("anio", f.get("anio"));
        m.put("tipo", f.get("tipo"));
        m.put("activa", f.get("activa"));
        boolean publicada = Boolean.TRUE.equals(f.get("publica"))
                && (Boolean.TRUE.equals(f.get("activa")) || Boolean.TRUE.equals(f.get("publicacion_independiente")));
        m.put("publicada", publicada);
        m.put("finalizada", f.get("finalizada"));
        m.put("descripcion", f.get("descripcion_publica"));
        m.put("lugar", f.get("lugar"));
        m.put("contacto", f.get("contacto"));
        m.put("fechasTexto", f.get("fechas_texto"));
        m.put("portadaUrl", archivo(f.get("portada_archivo")));
        m.put("dias", jdbc.query("""
                SELECT fecha, to_char(hora_inicio, 'HH24:MI') AS inicio, to_char(hora_fin, 'HH24:MI') AS fin
                  FROM edicion_dia WHERE id_edicion = ? ORDER BY fecha
                """, (rs, i) -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("fecha", rs.getString("fecha"));
            d.put("horaInicio", rs.getString("inicio"));
            d.put("horaFin", rs.getString("fin"));
            return d;
        }, id));
        m.put("vendeEntradas", f.get("vende_entradas"));
        m.put("vendeEspacios", f.get("vende_espacios"));
        m.put("tipoEspacio", f.get("tipo_espacio"));
        m.put("etiquetaEspacio", f.get("etiqueta_espacio"));
        m.put("comprador", f.get("comprador"));
        m.put("entradas", entradas(id));
        m.put("condiciones", f.get("condiciones"));
        m.put("minutosBloqueo", f.get("minutos_bloqueo"));
        Object hasta = f.get("venta_hasta");
        m.put("ventaHasta", hasta == null ? null : hasta.toString().replace(' ', 'T').substring(0, 16));
        m.put("pagoQrUrl", archivo(f.get("pago_qr_archivo")));
        m.put("pagoBanco", f.get("pago_banco"));
        m.put("pagoCuenta", f.get("pago_cuenta"));
        m.put("pagoTitular", f.get("pago_titular"));
        m.put("pagoInstrucciones", f.get("pago_instrucciones"));
        m.put("pagoOficina", f.get("pago_oficina"));
        m.put("usaCredenciales", f.get("usa_credenciales"));
        m.put("credencialEntrada", f.get("credencial_entrada"));
        m.put("credencialRepresentante", f.get("credencial_representante"));
        m.put("credencialAcompanantes", f.get("credencial_acompanantes"));
        m.put("configuracionPaso", f.get("configuracion_paso"));
        m.put("conPlano", f.get("con_plano"));
        m.put("categorias", categorias(id));
        m.put("textos", jdbc.queryForObject("""
                SELECT count(*) FROM plano_texto WHERE id_edicion = ? AND ("_estado" IS NULL OR "_estado" <> 'X')
                """, Long.class, id));
        m.put("disenosCredencial", jdbc.queryForObject(
                "SELECT count(*) FROM plantilla_credencial WHERE id_edicion = ?", Long.class, id));
        Map<String, Object> meta = metaCalculada(id);
        m.put("metaCalculada", meta.get("total"));
        m.put("metaEspacios", meta.get("espacios"));
        m.put("metaEntradas", meta.get("entradas"));
        m.put("entradasSinCupo", meta.get("sinCupo"));
        m.put("metaRecaudacion", f.get("meta_recaudacion"));
        m.put("metaAutomatica", f.get("meta_recaudacion") == null);
        m.put("pendientes", pendientes(m));
        return m;
    }

    private List<String> obligatoriosPendientes(Long id) {
        Map<String, Object> m = obtener(id);
        if (m == null) return null;
        List<String> faltan = new ArrayList<>();
        for (Object o : (List<?>) m.get("pendientes")) {
            Map<?, ?> p = (Map<?, ?>) o;
            if (Boolean.TRUE.equals(p.get("obligatorio")) && !Boolean.TRUE.equals(p.get("ok"))) faltan.add(String.valueOf(p.get("texto")));
        }
        return faltan;
    }

    private List<Map<String, Object>> pendientes(Map<String, Object> m) {
        List<Map<String, Object>> lista = new ArrayList<>();
        boolean entradas = Boolean.TRUE.equals(m.get("vendeEntradas"));
        boolean espacios = Boolean.TRUE.equals(m.get("vendeEspacios"));
        List<?> cats = (List<?>) m.get("categorias");
        long ubicados = cats.stream().mapToLong(c -> ((Number) ((Map<?, ?>) c).get("ubicados")).longValue()).sum();
        boolean conPrecio = ((BigDecimal) m.get("metaCalculada")).signum() > 0
                || cats.stream().anyMatch(c -> ((BigDecimal) ((Map<?, ?>) c).get("precio")).signum() > 0);
        boolean cobro = m.get("pagoQrUrl") != null || tiene(m.get("pagoCuenta")) || tiene(m.get("pagoOficina"));

        punto(lista, "datos", "Días de la actividad", !((List<?>) m.get("dias")).isEmpty(), true);
        punto(lista, "datos", "Dirección o lugar", tiene(m.get("lugar")), true);
        punto(lista, "datos", "Celular de contacto", tiene(m.get("contacto")), true);
        punto(lista, "venta", "Elegir qué se vende", entradas || espacios, true);
        if (entradas) punto(lista, "venta", "Al menos un tipo de entrada", !((List<?>) m.get("entradas")).isEmpty(), true);
        if (espacios) {
            punto(lista, "plano", "Al menos una categoría con precio", !cats.isEmpty(), true);
            punto(lista, "plano", "Espacios ubicados en el plano", ubicados > 0, true);
        }
        punto(lista, "condiciones", "Condiciones para el cliente", tiene(m.get("condiciones")), false);
        if (conPrecio) punto(lista, "cobro", "Cómo pagar: QR, cuenta u oficina", cobro, true);
        if (Boolean.TRUE.equals(m.get("usaCredenciales"))) {
            boolean alguna = Boolean.TRUE.equals(m.get("credencialEntrada")) || Boolean.TRUE.equals(m.get("credencialRepresentante"));
            punto(lista, "credenciales", "Elegir para quién son las credenciales", alguna, false);
        }
        return lista;
    }

    private static void punto(List<Map<String, Object>> lista, String paso, String texto, boolean ok, boolean obligatorio) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("paso", paso);
        p.put("texto", texto);
        p.put("ok", ok);
        p.put("obligatorio", obligatorio);
        lista.add(p);
    }

    private List<Map<String, Object>> entradas(Long id) {
        return jdbc.query("""
                SELECT id, nombre, descripcion, precio, cupo FROM entrada_tipo
                 WHERE id_edicion = ? AND "_estado" <> 'X' ORDER BY orden, id
                """, (rs, i) -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("id", rs.getLong("id"));
            e.put("nombre", rs.getString("nombre"));
            e.put("descripcion", rs.getString("descripcion"));
            e.put("precio", rs.getBigDecimal("precio"));
            e.put("cupo", rs.getObject("cupo"));
            return e;
        }, id);
    }

    private List<Map<String, Object>> categorias(Long id) {
        return jdbc.query("""
                SELECT c.id, c.nombre, c.descripcion, c.color, c.forma, COALESCE(c.precio_base, 0) AS precio,
                       c.personas_por_puesto,
                       count(p.id) FILTER (WHERE p."_estado" IS DISTINCT FROM 'X') AS cantidad,
                       count(p.id) FILTER (WHERE p."_estado" IS DISTINCT FROM 'X' AND p.mapa_x IS NOT NULL) AS ubicados,
                       count(p.id) FILTER (WHERE p."_estado" IS DISTINCT FROM 'X' AND p.estado_puesto = 'O') AS vendidos
                  FROM categoria c
                  LEFT JOIN puesto p ON p.id_categoria = c.id
                 WHERE c.id_edicion = ? AND c."_estado" IS DISTINCT FROM 'X'
                 GROUP BY c.id ORDER BY c.id
                """, (rs, i) -> {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("id", rs.getLong("id"));
            c.put("nombre", rs.getString("nombre"));
            c.put("descripcion", rs.getString("descripcion"));
            c.put("color", rs.getString("color"));
            c.put("forma", rs.getString("forma"));
            c.put("precio", rs.getBigDecimal("precio"));
            int personas = rs.getInt("personas_por_puesto");
            c.put("personasPorPuesto", personas < 1 ? 2 : personas);
            c.put("cantidad", rs.getLong("cantidad"));
            c.put("ubicados", rs.getLong("ubicados"));
            c.put("vendidos", rs.getLong("vendidos"));
            return c;
        }, id);
    }

    private Map<String, Object> metaCalculada(Long id) {
        BigDecimal espacios = jdbc.queryForObject("""
                SELECT COALESCE(sum(COALESCE(p.precio, c.precio_base, 0)), 0)
                  FROM puesto p JOIN categoria c ON c.id = p.id_categoria
                 WHERE c.id_edicion = ? AND c."_estado" IS DISTINCT FROM 'X'
                   AND p."_estado" IS DISTINCT FROM 'X' AND p.estado_puesto <> 'X'
                """, BigDecimal.class, id);
        Map<String, Object> e = jdbc.queryForMap("""
                SELECT COALESCE(sum(precio * cupo), 0) AS total, count(*) FILTER (WHERE cupo IS NULL AND precio > 0) AS sin_cupo
                  FROM entrada_tipo WHERE id_edicion = ? AND "_estado" <> 'X'
                """, id);
        Boolean conEspacios = jdbc.queryForObject("SELECT vende_espacios FROM edicion WHERE id = ?", Boolean.class, id);
        Boolean conEntradas = jdbc.queryForObject("SELECT vende_entradas FROM edicion WHERE id = ?", Boolean.class, id);
        BigDecimal deEspacios = Boolean.TRUE.equals(conEspacios) ? espacios : BigDecimal.ZERO;
        BigDecimal deEntradas = Boolean.TRUE.equals(conEntradas) ? (BigDecimal) e.get("total") : BigDecimal.ZERO;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("espacios", deEspacios);
        m.put("entradas", deEntradas);
        m.put("total", deEspacios.add(deEntradas));
        m.put("sinCupo", Boolean.TRUE.equals(conEntradas) ? ((Number) e.get("sin_cupo")).longValue() : 0L);
        return m;
    }

    private long contarEntradas(Long id) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM entrada_tipo WHERE id_edicion = ? AND \"_estado\" <> 'X'", Long.class, id);
        return n == null ? 0 : n;
    }

    private String guardarEntradas(Long id, List<Entrada> entradas, Long usuarioId) {
        if (entradas.size() > 20) return "Puedes definir hasta 20 tipos de entrada";
        Set<String> nombres = new HashSet<>();
        for (Entrada e : entradas) {
            String nombre = e == null ? null : limpio(e.nombre(), 80);
            if (nombre == null) return "Cada tipo de entrada necesita un nombre";
            if (!nombres.add(nombre.toLowerCase(ES))) return "Hay dos entradas llamadas «" + nombre + "»";
            if (e.precio() == null || e.precio().signum() < 0) return "Revisa el precio de «" + nombre + "»";
            if (e.cupo() != null && (e.cupo() < 1 || e.cupo() > 1_000_000)) return "Revisa el cupo de «" + nombre + "»";
        }
        List<Long> conservar = new ArrayList<>();
        int orden = 0;
        for (Entrada e : entradas) {
            orden++;
            String nombre = limpio(e.nombre(), 80);
            int n = e.id() == null ? 0 : jdbc.update("""
                    UPDATE entrada_tipo SET nombre = ?, descripcion = ?, precio = ?, cupo = ?, orden = ?
                     WHERE id = ? AND id_edicion = ? AND "_estado" <> 'X'
                    """, nombre, texto(e.descripcion(), 300), e.precio(), e.cupo(), orden, e.id(), id);
            if (n > 0) {
                conservar.add(e.id());
            } else {
                conservar.add(jdbc.queryForObject("""
                        INSERT INTO entrada_tipo (id_edicion, nombre, descripcion, precio, cupo, orden, "_registro_id_usuario")
                        VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                        """, Long.class, id, nombre, texto(e.descripcion(), 300), e.precio(), e.cupo(), orden, usuarioId));
            }
        }
        List<Long> actuales = jdbc.queryForList(
                "SELECT id FROM entrada_tipo WHERE id_edicion = ? AND \"_estado\" <> 'X'", Long.class, id);
        for (Long actual : actuales) {
            if (!conservar.contains(actual)) jdbc.update("UPDATE entrada_tipo SET \"_estado\" = 'X' WHERE id = ?", actual);
        }
        return null;
    }

    private static String validarDias(List<Dia> dias) {
        if (dias == null) return null;
        if (dias.size() > 60) return "Puedes registrar hasta 60 días";
        Set<LocalDate> vistos = new HashSet<>();
        for (Dia d : dias) {
            if (d == null || d.fecha() == null || d.fecha().isBlank()) return "Cada día necesita su fecha";
            LocalDate fecha;
            try {
                fecha = LocalDate.parse(d.fecha());
            } catch (DateTimeParseException e) {
                return "Una de las fechas no es válida";
            }
            if (!vistos.add(fecha)) return "El día " + fecha + " está repetido";
            LocalTime inicio = hora(d.horaInicio());
            LocalTime fin = hora(d.horaFin());
            if ((d.horaInicio() != null && !d.horaInicio().isBlank() && inicio == null)
                    || (d.horaFin() != null && !d.horaFin().isBlank() && fin == null)) {
                return "Revisa los horarios del " + fecha;
            }
        }
        return null;
    }

    private void guardarDias(Long id, List<Dia> dias) {
        jdbc.update("DELETE FROM edicion_dia WHERE id_edicion = ?", id);
        List<LocalDate> fechas = new ArrayList<>();
        for (Dia d : dias) {
            LocalDate fecha = LocalDate.parse(d.fecha());
            fechas.add(fecha);
            jdbc.update("INSERT INTO edicion_dia (id_edicion, fecha, hora_inicio, hora_fin) VALUES (?, ?, ?, ?)",
                    id, fecha, hora(d.horaInicio()), hora(d.horaFin()));
        }
        fechas.sort(null);
        if (!fechas.isEmpty()) {
            jdbc.update("UPDATE edicion SET fechas_texto = ?, anio = ? WHERE id = ?",
                    textoFechas(fechas), fechas.get(0).getYear(), id);
        }
    }

    static String textoFechas(List<LocalDate> fechas) {
        if (fechas.isEmpty()) return null;
        LocalDate primera = fechas.get(0);
        LocalDate ultima = fechas.get(fechas.size() - 1);
        boolean seguidas = primera.plusDays(fechas.size() - 1L).equals(ultima);
        String texto;
        if (fechas.size() == 1) {
            texto = primera.getDayOfMonth() + " de " + mes(primera);
        } else if (seguidas) {
            texto = primera.getMonth() == ultima.getMonth()
                    ? primera.getDayOfMonth() + " al " + ultima.getDayOfMonth() + " de " + mes(ultima)
                    : primera.getDayOfMonth() + " de " + mes(primera) + " al " + ultima.getDayOfMonth() + " de " + mes(ultima);
        } else {
            List<String> partes = new ArrayList<>();
            for (int i = 0; i < fechas.size(); i++) {
                LocalDate f = fechas.get(i);
                boolean finDeMes = i == fechas.size() - 1 || fechas.get(i + 1).getMonth() != f.getMonth();
                partes.add(finDeMes ? f.getDayOfMonth() + " de " + mes(f) : String.valueOf(f.getDayOfMonth()));
            }
            texto = partes.size() == 1 ? partes.get(0)
                    : String.join(", ", partes.subList(0, partes.size() - 1)) + " y " + partes.get(partes.size() - 1);
        }
        String resultado = texto + " de " + ultima.getYear();
        return resultado.length() > 120 ? resultado.substring(0, 120) : resultado;
    }

    private static String mes(LocalDate f) {
        return f.getMonth().getDisplayName(TextStyle.FULL, ES).toLowerCase(ES);
    }

    private static LocalTime hora(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalTime.parse(s.trim().length() == 5 ? s.trim() : s.trim().substring(0, 5));
        } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
            return null;
        }
    }

    private static String archivo(Object ruta) {
        return ruta == null ? null : "/files/" + ruta;
    }

    private static boolean tiene(Object o) {
        return o != null && !o.toString().isBlank();
    }

    private static String limpio(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String texto(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }
}

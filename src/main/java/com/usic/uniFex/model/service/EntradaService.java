package com.usic.uniFex.model.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Font.FontFamily;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EntradaService {

    public static final int MAXIMO_POR_COMPRA = 20;
    public static final int MAXIMO_EN_REVISION = 3;
    private static final Pattern CODIGO_PASE = Pattern.compile("ENT-[A-Z0-9]{8}-\\d{1,3}");
    private static final Pattern CODIGO_COMPRA = Pattern.compile("ENT-[A-Z0-9]{8}");
    private static final Pattern NOMBRE = Pattern.compile("^[\\p{L} .'-]{3,160}$");
    private static final Pattern CELULAR = Pattern.compile("^\\+?\\d{7,15}$");
    private static final Pattern CI = Pattern.compile("^[0-9A-Za-z-]{4,20}$");
    private static final Set<String> TIPOS_COMPROBANTE = Set.of("image/png", "image/jpeg", "image/webp", "application/pdf");
    private static final String LETRAS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final long REPETIDA_MS = 15_000;
    private static final SecureRandom AZAR = new SecureRandom();
    private static final BaseColor TINTA = new BaseColor(17, 24, 39);
    private static final BaseColor TINTA_SUAVE = new BaseColor(107, 114, 128);
    private static final BaseColor GRIS_LINEA = new BaseColor(210, 214, 220);
    private static final BaseColor GRIS_FONDO = new BaseColor(243, 244, 246);
    private static final DateTimeFormatter F_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final JdbcTemplate jdbc;
    private final FileStorageService archivos;
    private final AvisoPublicoService avisoPublico;
    private final EntradaDisenoService disenos;

    public record Item(Long tipoId, Integer cantidad) {
    }

    public record Compra(String nombre, String ci, String celular, String modoPago, Boolean acepta, List<Item> items,
                         String celularExtra) {
        public Compra(String nombre, String ci, String celular, String modoPago, Boolean acepta, List<Item> items) {
            this(nombre, ci, celular, modoPago, acepta, items, null);
        }
    }

    public record Resultado(boolean ok, String mensaje, Map<String, Object> datos) {
        static Resultado error(String m) { return new Resultado(false, m, null); }
        static Resultado ok(String m, Map<String, Object> d) { return new Resultado(true, m, d); }
    }

    public static boolean esCodigoDePase(String codigo) {
        return codigo != null && CODIGO_PASE.matcher(codigo).matches();
    }

    @Transactional
    public Resultado comprarEnLinea(Long edicion, Compra c, MultipartFile comprobante, String origen) throws IOException {
        Boolean abierta = jdbc.query("""
                SELECT vende_entradas AND publica AND (activa OR publicacion_independiente) AND NOT finalizada
                       AND (venta_hasta IS NULL OR venta_hasta > now()) AND (venta_desde IS NULL OR venta_desde <= now())
                  FROM edicion WHERE id = ?
                """, rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE, edicion);
        if (!Boolean.TRUE.equals(abierta)) return Resultado.error("La venta de entradas de esta actividad está cerrada.");
        String error = validarComprador(c, true);
        if (error != null) return Resultado.error(error);
        if (!Boolean.TRUE.equals(c.acepta())) return Resultado.error("Acepta los términos y condiciones y la política de privacidad para continuar.");
        Integer enRevision = jdbc.queryForObject(
                "SELECT count(*) FROM entrada_compra WHERE id_edicion = ? AND celular = ? AND estado = 'REVISION'",
                Integer.class, edicion, c.celular().trim());
        if (enRevision != null && enRevision >= MAXIMO_EN_REVISION) {
            return Resultado.error("Ya tienes " + MAXIMO_EN_REVISION + " compras en revisión. Espera a que te confirmemos.");
        }
        return registrar(edicion, c, comprobante, origen, null);
    }

    @Transactional
    public Resultado venderEnOficina(Long edicion, Compra c, MultipartFile comprobante, Long usuarioId) throws IOException {
        Boolean abierta = jdbc.query("""
                SELECT vende_entradas AND (activa OR publicacion_independiente) AND NOT finalizada FROM edicion WHERE id = ?
                """, rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE, edicion);
        if (!Boolean.TRUE.equals(abierta)) return Resultado.error("Esta actividad no está vendiendo entradas.");
        String error = validarComprador(c, false);
        if (error != null) return Resultado.error(error);
        return registrar(edicion, c, comprobante, "oficina", usuarioId);
    }

    private Resultado registrar(Long edicion, Compra c, MultipartFile comprobante, String origen, Long usuarioId) throws IOException {
        if (c.items() == null || c.items().isEmpty()) return Resultado.error("Elige al menos una entrada.");
        Map<Long, Integer> pedidas = new TreeMap<>();
        for (Item i : c.items()) {
            if (i == null || i.tipoId() == null || i.cantidad() == null || i.cantidad() < 0) return Resultado.error("Revisa las cantidades.");
            if (i.cantidad() > 0) pedidas.merge(i.tipoId(), i.cantidad(), Integer::sum);
        }
        int total = pedidas.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) return Resultado.error("Elige al menos una entrada.");
        if (total > MAXIMO_POR_COMPRA) return Resultado.error("Puedes comprar hasta " + MAXIMO_POR_COMPRA + " entradas por vez.");

        BigDecimal monto = BigDecimal.ZERO;
        List<Object[]> detalle = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : pedidas.entrySet()) {
            List<Map<String, Object>> tipo = jdbc.queryForList("""
                    SELECT id, nombre, precio, cupo FROM entrada_tipo
                     WHERE id = ? AND id_edicion = ? AND "_estado" <> 'X' FOR UPDATE
                    """, e.getKey(), edicion);
            if (tipo.isEmpty()) return Resultado.error("Uno de los tipos de entrada ya no está a la venta.");
            Map<String, Object> t = tipo.get(0);
            Integer cupo = (Integer) t.get("cupo");
            if (cupo != null) {
                int ocupadas = ocupadas(e.getKey());
                if (ocupadas + e.getValue() > cupo) {
                    int quedan = Math.max(0, cupo - ocupadas);
                    return Resultado.error(quedan == 0 ? "Se agotaron las entradas «" + t.get("nombre") + "»."
                            : "Solo quedan " + quedan + " entradas «" + t.get("nombre") + "».");
                }
            }
            BigDecimal precio = (BigDecimal) t.get("precio");
            monto = monto.add(precio.multiply(BigDecimal.valueOf(e.getValue())));
            detalle.add(new Object[] { e.getKey(), e.getValue(), precio });
        }

        String modo;
        boolean enLinea = usuarioId == null;
        if (monto.signum() == 0) {
            modo = "GRATIS";
        } else {
            modo = c.modoPago() == null ? "" : c.modoPago().trim().toUpperCase(Locale.ROOT);
            Set<String> validos = enLinea ? Set.of("COMPROBANTE", "OFICINA") : Set.of("EFECTIVO", "QR", "COMPROBANTE");
            if (!validos.contains(modo)) return Resultado.error("Elige cómo vas a pagar.");
            if ("OFICINA".equals(modo) && !tieneOficina(edicion)) return Resultado.error("Esta actividad no recibe pagos en persona.");
        }
        boolean conArchivo = comprobante != null && !comprobante.isEmpty();
        if (enLinea && "COMPROBANTE".equals(modo) && !conArchivo) return Resultado.error("Adjunta la foto o PDF de tu comprobante de pago.");
        if (conArchivo && !TIPOS_COMPROBANTE.contains(String.valueOf(comprobante.getContentType()))) {
            return Resultado.error("El comprobante debe ser una foto (PNG, JPG o WEBP) o un PDF.");
        }
        if (conArchivo && comprobante.getSize() > 8L * 1024 * 1024) return Resultado.error("El comprobante no puede pasar de 8 MB.");

        boolean aprobada = !enLinea || "GRATIS".equals(modo);
        String codigo = nuevoCodigo();
        String archivo = conArchivo ? archivos.save(comprobante, FileStorageService.Bucket.COMPROBANTES, "entrada") : null;
        Long id = jdbc.queryForObject("""
                INSERT INTO entrada_compra (id_edicion, codigo, nombre, ci, celular, total, estado, modo_pago,
                                            comprobante_archivo, origen, registrado_por, validado_por, validado_en, celular_extra)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, edicion, codigo, nombre(c.nombre()), vacio(c.ci()), c.celular().trim(), monto,
                aprobada ? "APROBADA" : "REVISION", modo, archivo, recortar(origen, 60), usuarioId,
                aprobada ? usuarioId : null, aprobada ? new Timestamp(System.currentTimeMillis()) : null,
                celularExtra(c));
        for (Object[] d : detalle) {
            jdbc.update("INSERT INTO entrada_compra_detalle (id_compra, id_tipo, cantidad, precio) VALUES (?, ?, ?, ?)",
                    id, d[0], d[1], d[2]);
        }
        if (aprobada) generarPases(id, codigo);
        HistorialService.anotarResumen(codigo + " · " + nombre(c.nombre()) + " · " + total + " entrada(s) · Bs " + monto + " · " + modo);
        avisoPublico.cambio("entradas", edicion);
        log.info("Compra de entradas {} ({} entradas, Bs {}) registrada {}", codigo, total, monto, enLinea ? "en línea" : "en oficina");
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("id", id);
        datos.put("codigo", codigo);
        datos.put("estado", aprobada ? "APROBADA" : "REVISION");
        datos.put("total", monto);
        datos.put("cantidad", total);
        return Resultado.ok(aprobada ? "Compra aprobada" : "Compra enviada", datos);
    }

    @Transactional
    public Resultado aprobar(Long id, Long usuarioId) {
        List<Map<String, Object>> f = jdbc.queryForList("SELECT codigo, id_edicion, nombre, total FROM entrada_compra WHERE id = ?", id);
        if (f.isEmpty()) return Resultado.error("La compra ya no existe.");
        int n = jdbc.update("""
                UPDATE entrada_compra SET estado = 'APROBADA', validado_por = ?, validado_en = now(), motivo = NULL
                 WHERE id = ? AND estado = 'REVISION'
                """, usuarioId, id);
        if (n == 0) return Resultado.error("Esta compra ya fue atendida por otra persona.");
        String codigo = (String) f.get(0).get("codigo");
        HistorialService.anotarResumen(codigo + " · " + f.get(0).get("nombre") + " · Bs " + f.get(0).get("total"));
        generarPases(id, codigo);
        avisoPublico.cambio("entradas", ((Number) f.get(0).get("id_edicion")).longValue());
        return Resultado.ok("Compra aprobada: se generaron sus pases.", Map.of("codigo", codigo));
    }

    @Transactional
    public Resultado rechazar(Long id, String motivo, Long usuarioId) {
        String m = recortar(motivo, 300);
        if (m == null) return Resultado.error("Escribe el motivo para avisarle al cliente.");
        HistorialService.anotarResumen("Motivo: " + m);
        List<Long> ed = jdbc.queryForList("SELECT id_edicion FROM entrada_compra WHERE id = ?", Long.class, id);
        int n = jdbc.update("""
                UPDATE entrada_compra SET estado = 'RECHAZADA', motivo = ?, validado_por = ?, validado_en = now()
                 WHERE id = ? AND estado = 'REVISION'
                """, m, usuarioId, id);
        if (n == 0) return Resultado.error("Esta compra ya fue atendida por otra persona.");
        avisoPublico.cambio("entradas", ed.get(0));
        return Resultado.ok("Compra rechazada: sus entradas vuelven a estar disponibles.", Map.of());
    }

    @Transactional
    public Resultado anular(Long id, String motivo, Long usuarioId) {
        String m = recortar(motivo, 300);
        if (m == null) return Resultado.error("Escribe el motivo de la anulación.");
        HistorialService.anotarResumen("Motivo: " + m);
        Integer usados = jdbc.queryForObject(
                "SELECT count(*) FROM entrada_pase WHERE id_compra = ? AND primer_ingreso IS NOT NULL", Integer.class, id);
        if (usados != null && usados > 0) return Resultado.error("No se puede anular: " + usados + " pase(s) ya ingresaron.");
        List<Long> ed = jdbc.queryForList("SELECT id_edicion FROM entrada_compra WHERE id = ?", Long.class, id);
        int n = jdbc.update("""
                UPDATE entrada_compra SET estado = 'ANULADA', motivo = ?, validado_por = ?, validado_en = now()
                 WHERE id = ? AND estado = 'APROBADA'
                """, m, usuarioId, id);
        if (n == 0) return Resultado.error("Solo se anulan compras aprobadas.");
        jdbc.update("DELETE FROM entrada_pase WHERE id_compra = ?", id);
        avisoPublico.cambio("entradas", ed.get(0));
        return Resultado.ok("Compra anulada.", Map.of());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> ultimaEnRevision() {
        return jdbc.queryForList("""
                SELECT c.id, c.codigo, c.nombre, c.total, c.id_edicion AS edicion, e.nombre AS actividad,
                       (SELECT sum(d.cantidad) FROM entrada_compra_detalle d WHERE d.id_compra = c.id) AS cantidad
                  FROM entrada_compra c JOIN edicion e ON e.id = c.id_edicion
                 WHERE c.estado = 'REVISION' ORDER BY c.creado_en DESC, c.id DESC LIMIT 1
                """).stream().findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public long enRevision() {
        Long n = jdbc.queryForObject("SELECT count(*) FROM entrada_compra WHERE estado = 'REVISION'", Long.class);
        return n == null ? 0 : n;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> disponibles(Long edicion) {
        return jdbc.query("""
                SELECT t.id, t.nombre, t.descripcion, t.precio, t.cupo,
                       COALESCE((SELECT sum(d.cantidad) FROM entrada_compra_detalle d JOIN entrada_compra c ON c.id = d.id_compra
                                  WHERE d.id_tipo = t.id AND c.estado IN ('REVISION', 'APROBADA')), 0) AS ocupadas
                  FROM entrada_tipo t
                 WHERE t.id_edicion = ? AND t."_estado" <> 'X' ORDER BY t.orden, t.id
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("nombre", rs.getString("nombre"));
            m.put("descripcion", rs.getString("descripcion"));
            m.put("precio", rs.getBigDecimal("precio"));
            Integer cupo = (Integer) rs.getObject("cupo");
            long ocupadas = rs.getLong("ocupadas");
            m.put("cupo", cupo);
            m.put("quedan", cupo == null ? null : Math.max(0, cupo - ocupadas));
            return m;
        }, edicion);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listar(Long edicion, String estado) {
        String filtro = estado == null || estado.isBlank() ? "" : " AND c.estado = ?";
        Object[] args = filtro.isEmpty() ? new Object[] { edicion } : new Object[] { edicion, estado.trim().toUpperCase(Locale.ROOT) };
        return jdbc.query("""
                SELECT c.*, u.username AS validador, r.username AS vendedor,
                       (SELECT string_agg(d.cantidad || ' × ' || t.nombre, ', ' ORDER BY t.orden, t.id)
                          FROM entrada_compra_detalle d JOIN entrada_tipo t ON t.id = d.id_tipo WHERE d.id_compra = c.id) AS resumen,
                       (SELECT sum(d.cantidad) FROM entrada_compra_detalle d WHERE d.id_compra = c.id) AS cantidad,
                       (SELECT count(*) FROM entrada_pase p WHERE p.id_compra = c.id AND p.primer_ingreso IS NOT NULL) AS ingresaron
                  FROM entrada_compra c
                  LEFT JOIN usuario u ON u.id = c.validado_por
                  LEFT JOIN usuario r ON r.id = c.registrado_por
                 WHERE c.id_edicion = ?""" + filtro + " ORDER BY c.creado_en DESC", (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("codigo", rs.getString("codigo"));
            m.put("nombre", rs.getString("nombre"));
            m.put("ci", rs.getString("ci"));
            m.put("celular", rs.getString("celular"));
            m.put("celularExtra", rs.getString("celular_extra"));
            m.put("total", rs.getBigDecimal("total"));
            m.put("estado", rs.getString("estado"));
            m.put("modoPago", rs.getString("modo_pago"));
            String archivo = rs.getString("comprobante_archivo");
            m.put("comprobanteUrl", archivo == null ? null : "/files/" + archivo);
            m.put("motivo", rs.getString("motivo"));
            m.put("resumen", rs.getString("resumen"));
            m.put("cantidad", rs.getInt("cantidad"));
            m.put("ingresaron", rs.getInt("ingresaron"));
            m.put("validador", rs.getString("validador"));
            m.put("vendedor", rs.getString("vendedor"));
            Timestamp creado = rs.getTimestamp("creado_en");
            m.put("creadoEn", creado == null ? null : creado.toLocalDateTime().toString());
            Timestamp validado = rs.getTimestamp("validado_en");
            m.put("validadoEn", validado == null ? null : validado.toLocalDateTime().toString());
            return m;
        }, args);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> consultar(String codigo) {
        String limpio = codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
        if (!CODIGO_COMPRA.matcher(limpio).matches()) return null;
        List<Map<String, Object>> f = jdbc.queryForList("""
                SELECT c.id, c.codigo, c.nombre, c.estado, c.total, c.motivo, c.modo_pago, c.creado_en, e.id AS edicion,
                       e.nombre AS actividad, e.lugar, e.fechas_texto, e.contacto
                  FROM entrada_compra c JOIN edicion e ON e.id = c.id_edicion WHERE c.codigo = ?
                """, limpio);
        if (f.isEmpty()) return null;
        Map<String, Object> c = f.get(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("codigo", c.get("codigo"));
        m.put("nombre", c.get("nombre"));
        m.put("estado", c.get("estado"));
        m.put("total", c.get("total"));
        m.put("motivo", c.get("motivo"));
        m.put("modoPago", c.get("modo_pago"));
        m.put("edicion", c.get("edicion"));
        m.put("actividad", c.get("actividad"));
        m.put("lugar", c.get("lugar"));
        m.put("fechas", c.get("fechas_texto"));
        m.put("contacto", c.get("contacto"));
        m.put("detalle", jdbc.queryForList("""
                SELECT t.nombre, d.cantidad, d.precio FROM entrada_compra_detalle d JOIN entrada_tipo t ON t.id = d.id_tipo
                 WHERE d.id_compra = ? ORDER BY t.orden, t.id
                """, c.get("id")));
        m.put("pases", "APROBADA".equals(c.get("estado")) ? jdbc.queryForList("""
                SELECT p.numero, p.codigo, t.nombre AS tipo, p.dentro FROM entrada_pase p JOIN entrada_tipo t ON t.id = p.id_tipo
                 WHERE p.id_compra = ? ORDER BY p.numero
                """, c.get("id")) : List.of());
        return m;
    }

    @Transactional
    public Map<String, Object> registrarIngreso(String codigo, Long usuarioId) {
        return registrarIngreso(codigo, null, usuarioId);
    }

    @Transactional
    public Map<String, Object> registrarIngreso(String codigo, String sentidoPedido, Long usuarioId) {
        String limpio = codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT);
        List<Map<String, Object>> f = jdbc.queryForList("""
                SELECT p.id, p.numero, p.dentro, p.primer_ingreso, t.nombre AS tipo, c.nombre, c.codigo AS compra, c.estado,
                       e.nombre AS actividad, (SELECT count(*) FROM entrada_pase x WHERE x.id_compra = c.id) AS total,
                       (SELECT a.cuando FROM entrada_pase_acceso a WHERE a.id_pase = p.id ORDER BY a.cuando DESC, a.id DESC LIMIT 1) AS ultimo
                  FROM entrada_pase p
                  JOIN entrada_compra c ON c.id = p.id_compra
                  JOIN entrada_tipo t ON t.id = p.id_tipo
                  JOIN edicion e ON e.id = c.id_edicion
                 WHERE p.codigo = ?
                 FOR UPDATE OF p
                """, limpio);
        Map<String, Object> res = new LinkedHashMap<>();
        if (f.isEmpty()) {
            res.put("ok", false);
            res.put("mensaje", "Entrada no válida");
            return res;
        }
        Map<String, Object> p = f.get(0);
        res.put("tipo", "entrada");
        res.put("entrada", p.get("tipo"));
        res.put("nombre", p.get("nombre"));
        res.put("actividad", p.get("actividad"));
        res.put("compra", p.get("compra"));
        res.put("pase", p.get("numero") + " de " + p.get("total"));
        if (!"APROBADA".equals(p.get("estado"))) {
            res.put("ok", false);
            res.put("mensaje", "Esta compra no está aprobada (" + p.get("estado") + ")");
            return res;
        }
        boolean dentro = Boolean.TRUE.equals(p.get("dentro"));
        Timestamp ultimo = (Timestamp) p.get("ultimo");
        if (ultimo != null && System.currentTimeMillis() - ultimo.getTime() < REPETIDA_MS) {
            res.put("ok", false);
            res.put("sentido", dentro ? "E" : "S");
            res.put("mensaje", "Lectura repetida: esta entrada se registró hace unos segundos");
            return res;
        }
        Timestamp primero = (Timestamp) p.get("primer_ingreso");
        if ("E".equals(sentidoPedido) && dentro) {
            res.put("ok", false);
            res.put("yaDentro", true);
            res.put("sentido", "E");
            res.put("mensaje", "Este QR ya se usó para entrar" + (ultimo != null ? " a las " + ultimo.toLocalDateTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "")
                    + " y la persona sigue dentro. No dejes pasar a otra persona con el mismo QR.");
            return res;
        }
        if ("S".equals(sentidoPedido) && !dentro) {
            res.put("ok", false);
            res.put("sentido", "S");
            res.put("mensaje", primero == null ? "Esta entrada todavía no se usó para entrar." : "Esta entrada ya figura fuera.");
            return res;
        }
        String sentido = dentro ? "S" : "E";
        jdbc.update("UPDATE entrada_pase SET dentro = ?, primer_ingreso = COALESCE(primer_ingreso, CASE WHEN ? THEN now() END) WHERE id = ?",
                !dentro, !dentro, p.get("id"));
        jdbc.update("INSERT INTO entrada_pase_acceso (id_pase, sentido, registrado_por) VALUES (?, ?, ?)", p.get("id"), sentido, usuarioId);
        res.put("ok", true);
        res.put("sentido", sentido);
        res.put("mensaje", dentro ? "Salida registrada" : p.get("primer_ingreso") == null ? "Ingreso registrado" : "Vuelve a ingresar");
        return res;
    }

    private List<Map<String, Object>> pasesDe(String codigoCompra) {
        return jdbc.queryForList("""
                SELECT p.numero, p.codigo, t.nombre AS tipo, c.nombre, c.ci, c.id_edicion,
                       (SELECT count(*) FROM entrada_pase x WHERE x.id_compra = c.id) AS total
                  FROM entrada_pase p
                  JOIN entrada_compra c ON c.id = p.id_compra
                  JOIN entrada_tipo t ON t.id = p.id_tipo
                 WHERE c.codigo = ? AND c.estado = 'APROBADA' ORDER BY p.numero
                """, codigoCompra == null ? "" : codigoCompra.trim().toUpperCase(Locale.ROOT));
    }

    private byte[] imagenDe(Map<String, Object> p) {
        return disenos.dibujar(((Number) p.get("id_edicion")).longValue(), new EntradaDisenoService.Pase(
                texto(p.get("codigo")), texto(p.get("nombre")), texto(p.get("ci")), texto(p.get("tipo")),
                ((Number) p.get("numero")).intValue(), ((Number) p.get("total")).intValue()));
    }

    @Transactional(readOnly = true)
    public byte[] pngPase(String codigoCompra, int numero) {
        return pasesDe(codigoCompra).stream().filter(p -> ((Number) p.get("numero")).intValue() == numero)
                .findFirst().map(this::imagenDe).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<byte[]> pngPases(String codigoCompra) {
        return pasesDe(codigoCompra).stream().map(this::imagenDe).toList();
    }

    @Transactional(readOnly = true)
    public byte[] pdfPases(String codigoCompra) {
        List<Map<String, Object>> pases = pasesDe(codigoCompra);
        if (pases.isEmpty()) return null;
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Document doc = null;
            for (Map<String, Object> p : pases) {
                Image img = Image.getInstance(imagenDe(p));
                float ancho = 360f;
                float alto = ancho * img.getHeight() / img.getWidth();
                Rectangle pagina = new Rectangle(ancho, alto);
                if (doc == null) {
                    doc = new Document(pagina, 0, 0, 0, 0);
                    PdfWriter.getInstance(doc, salida);
                    doc.open();
                } else {
                    doc.setPageSize(pagina);
                    doc.newPage();
                }
                img.scaleAbsolute(ancho, alto);
                img.setAbsolutePosition(0, 0);
                doc.add(img);
            }
            doc.close();
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudieron generar las entradas: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] recibo(String codigoCompra) {
        String codigo = codigoCompra == null ? "" : codigoCompra.trim().toUpperCase(Locale.ROOT);
        List<Map<String, Object>> f = jdbc.queryForList("""
                SELECT c.*, e.nombre AS actividad, e.lugar, e.fechas_texto, u.username AS validador
                  FROM entrada_compra c JOIN edicion e ON e.id = c.id_edicion
                  LEFT JOIN usuario u ON u.id = c.validado_por
                 WHERE c.codigo = ?
                """, codigo);
        if (f.isEmpty()) return null;
        Map<String, Object> d = f.get(0);
        List<Map<String, Object>> detalle = jdbc.queryForList("""
                SELECT t.nombre, d.cantidad, d.precio FROM entrada_compra_detalle d JOIN entrada_tipo t ON t.id = d.id_tipo
                 WHERE d.id_compra = ? ORDER BY t.orden, t.id
                """, d.get("id"));
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A5, 30, 30, 30, 30);
            PdfWriter.getInstance(doc, salida);
            doc.open();
            Font marca = new Font(FontFamily.HELVETICA, 13, Font.BOLD, TINTA);
            Font sub = new Font(FontFamily.HELVETICA, 9, Font.BOLD, TINTA_SUAVE);
            Font etiqueta = new Font(FontFamily.HELVETICA, 8, Font.BOLD, TINTA);
            Font normal = new Font(FontFamily.HELVETICA, 8.5f, Font.NORMAL, TINTA);
            Font chico = new Font(FontFamily.HELVETICA, 7, Font.NORMAL, TINTA_SUAVE);
            Font grande = new Font(FontFamily.HELVETICA, 12, Font.BOLD, TINTA);

            Paragraph cab = new Paragraph();
            cab.add(new Phrase("RECIBO DE ENTRADAS\n", marca));
            cab.add(new Phrase(texto(d.get("actividad")) + "\n", sub));
            cab.add(new Phrase(String.join(" · ", texto(d.get("fechas_texto")), texto(d.get("lugar"))), chico));
            doc.add(cab);
            String estado = texto(d.get("estado"));
            if (!"APROBADA".equals(estado)) {
                Paragraph aviso = new Paragraph("COMPRA " + ("REVISION".equals(estado) ? "EN REVISIÓN" : estado),
                        new Font(FontFamily.HELVETICA, 10, Font.BOLD, new BaseColor(185, 28, 28)));
                aviso.setSpacingBefore(6);
                doc.add(aviso);
            }
            PdfPTable cli = new PdfPTable(new float[] { 28, 72 });
            cli.setWidthPercentage(100);
            cli.setSpacingBefore(8);
            par(cli, "Código", codigo, etiqueta, normal);
            par(cli, "Comprador", texto(d.get("nombre")), etiqueta, normal);
            par(cli, "C.I.", texto(d.get("ci")), etiqueta, normal);
            par(cli, "Celular", texto(d.get("celular")), etiqueta, normal);
            par(cli, "Pago", modoTexto(texto(d.get("modo_pago"))), etiqueta, normal);
            Timestamp validado = (Timestamp) d.get("validado_en");
            par(cli, "Aprobó", d.get("validador") == null ? "—"
                    : d.get("validador") + (validado == null ? "" : " · " + validado.toLocalDateTime().format(F_HORA)), etiqueta, normal);
            doc.add(cli);

            PdfPTable det = new PdfPTable(new float[] { 50, 15, 35 });
            det.setWidthPercentage(100);
            det.setSpacingBefore(8);
            for (Map<String, Object> l : detalle) {
                BigDecimal precio = (BigDecimal) l.get("precio");
                int cantidad = ((Number) l.get("cantidad")).intValue();
                det.addCell(celda(texto(l.get("nombre")), normal, Element.ALIGN_LEFT));
                det.addCell(celda("× " + cantidad, normal, Element.ALIGN_CENTER));
                det.addCell(celda(bs(precio.multiply(BigDecimal.valueOf(cantidad))) + " Bs", normal, Element.ALIGN_RIGHT));
            }
            PdfPCell t1 = new PdfPCell(new Phrase("TOTAL", grande));
            t1.setColspan(2);
            t1.setBorder(Rectangle.NO_BORDER);
            t1.setBackgroundColor(GRIS_FONDO);
            t1.setPadding(6);
            det.addCell(t1);
            PdfPCell t2 = new PdfPCell(new Phrase(bs((BigDecimal) d.get("total")) + " Bs", grande));
            t2.setBorder(Rectangle.NO_BORDER);
            t2.setBackgroundColor(GRIS_FONDO);
            t2.setPadding(6);
            t2.setHorizontalAlignment(Element.ALIGN_RIGHT);
            det.addCell(t2);
            doc.add(det);

            Image qr = new BarcodeQRCode(codigo, 140, 140, null).getImage();
            qr.scaleAbsolute(80, 80);
            qr.setAlignment(Element.ALIGN_CENTER);
            qr.setSpacingBefore(8);
            doc.add(qr);
            Paragraph pie = new Paragraph("Presenta tus pases en la puerta; cada pase permite un ingreso.", chico);
            pie.setAlignment(Element.ALIGN_CENTER);
            doc.add(pie);
            doc.close();
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el recibo: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resumen(Long edicion) {
        Map<String, Object> m = new LinkedHashMap<>(jdbc.queryForMap("""
                SELECT COALESCE(sum(total) FILTER (WHERE estado = 'APROBADA'), 0) AS recaudado,
                       COALESCE(sum(total) FILTER (WHERE estado = 'REVISION'), 0) AS por_validar_monto,
                       count(*) FILTER (WHERE estado = 'REVISION') AS por_validar,
                       count(*) FILTER (WHERE estado = 'APROBADA') AS aprobadas
                  FROM entrada_compra WHERE id_edicion = ?
                """, edicion));
        m.put("vendidas", jdbc.queryForObject("""
                SELECT COALESCE(sum(d.cantidad), 0) FROM entrada_compra_detalle d JOIN entrada_compra c ON c.id = d.id_compra
                 WHERE c.id_edicion = ? AND c.estado = 'APROBADA'
                """, Long.class, edicion));
        m.put("tipos", disponibles(edicion));
        return m;
    }

    private void generarPases(Long compraId, String codigo) {
        List<Map<String, Object>> detalle = jdbc.queryForList("""
                SELECT d.id_tipo, d.cantidad FROM entrada_compra_detalle d JOIN entrada_tipo t ON t.id = d.id_tipo
                 WHERE d.id_compra = ? ORDER BY t.orden, t.id
                """, compraId);
        int numero = 0;
        for (Map<String, Object> d : detalle) {
            int cantidad = ((Number) d.get("cantidad")).intValue();
            for (int i = 0; i < cantidad; i++) {
                numero++;
                jdbc.update("""
                        INSERT INTO entrada_pase (id_compra, id_tipo, numero, codigo) VALUES (?, ?, ?, ?)
                        ON CONFLICT (id_compra, numero) DO NOTHING
                        """, compraId, d.get("id_tipo"), numero, codigo + "-" + numero);
            }
        }
    }

    private int ocupadas(Long tipoId) {
        Integer n = jdbc.queryForObject("""
                SELECT COALESCE(sum(d.cantidad), 0) FROM entrada_compra_detalle d JOIN entrada_compra c ON c.id = d.id_compra
                 WHERE d.id_tipo = ? AND c.estado IN ('REVISION', 'APROBADA')
                """, Integer.class, tipoId);
        return n == null ? 0 : n;
    }

    private String nuevoCodigo() {
        for (int intento = 0; intento < 10; intento++) {
            StringBuilder s = new StringBuilder("ENT-");
            for (int i = 0; i < 8; i++) s.append(LETRAS.charAt(AZAR.nextInt(LETRAS.length())));
            Integer existe = jdbc.queryForObject("SELECT count(*) FROM entrada_compra WHERE codigo = ?", Integer.class, s.toString());
            if (existe == null || existe == 0) return s.toString();
        }
        throw new IllegalStateException("No se pudo generar un código de compra");
    }

    private boolean tieneOficina(Long edicion) {
        Boolean t = jdbc.queryForObject("SELECT pago_oficina IS NOT NULL AND btrim(pago_oficina) <> '' FROM edicion WHERE id = ?", Boolean.class, edicion);
        return Boolean.TRUE.equals(t);
    }

    private static String celularExtra(Compra c) {
        if (c.celularExtra() == null) return null;
        String extra = c.celularExtra().replaceAll("\\s", "");
        return CELULAR.matcher(extra).matches() && !extra.equals(c.celular().trim()) ? extra : null;
    }

    private static String validarComprador(Compra c, boolean pideCi) {
        if (c == null) return "Faltan tus datos.";
        if (c.nombre() == null || !NOMBRE.matcher(c.nombre().trim()).matches()) return "Escribe tu nombre completo (solo letras).";
        if (c.celular() == null || !CELULAR.matcher(c.celular().trim()).matches()) return "Escribe un celular válido (7 a 15 números).";
        boolean conCi = c.ci() != null && !c.ci().isBlank();
        if ((pideCi || conCi) && (!conCi || !CI.matcher(c.ci().trim()).matches())) return "Escribe tu número de carnet (C.I.).";
        return null;
    }

    private static String modoTexto(String modo) {
        return switch (modo) {
            case "EFECTIVO" -> "En efectivo (punto de venta)";
            case "QR" -> "QR en punto de venta";
            case "COMPROBANTE" -> "Transferencia o QR con comprobante";
            case "OFICINA" -> "En oficina";
            case "GRATIS" -> "Sin costo";
            default -> "—";
        };
    }

    private static String nombre(String s) {
        return s.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String recortar(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static PdfPCell celda(String texto, Font f, int alineacion) {
        PdfPCell c = new PdfPCell(new Phrase(texto, f));
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(GRIS_LINEA);
        c.setPadding(4);
        c.setHorizontalAlignment(alineacion);
        return c;
    }

    private static void par(PdfPTable t, String etiqueta, String valor, Font fe, Font fv) {
        t.addCell(celda(etiqueta, fe, Element.ALIGN_LEFT));
        t.addCell(celda(valor == null || valor.isBlank() ? "—" : valor, fv, Element.ALIGN_LEFT));
    }

    private static String texto(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String bs(BigDecimal v) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.of("es", "BO"));
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return f.format(v == null ? BigDecimal.ZERO : v);
    }
}

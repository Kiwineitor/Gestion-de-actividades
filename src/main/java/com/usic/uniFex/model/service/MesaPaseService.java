package com.usic.uniFex.model.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
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
import com.usic.uniFex.model.dto.PlantillaCredencial;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MesaPaseService {

    private static final java.util.regex.Pattern CODIGO_PASE = java.util.regex.Pattern.compile("MSA-[A-Z0-9]{10}-\\d{1,2}");
    private static final BaseColor TINTA = new BaseColor(17, 24, 39);
    private static final BaseColor TINTA_SUAVE = new BaseColor(107, 114, 128);
    private static final BaseColor GRIS_LINEA = new BaseColor(210, 214, 220);
    private static final BaseColor GRIS_FONDO = new BaseColor(243, 244, 246);
    private static final DateTimeFormatter F_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final String SELECT_PASE = """
            SELECT p.numero, p.codigo AS pase, p.tipo, r.id AS reserva_id, r.codigo AS reserva, r.nombre, r.apellidos,
                   r.ci, r.estado, r.id_evento, r.personas, m.codigo AS mesa, m.zona, e.nombre AS evento,
                   p.nombre AS p_nombre, p.ci AS p_ci, p.foto AS p_foto, e.local_nombre, e.logo_archivo,
                   (SELECT count(*) FROM mesa_pase x WHERE x.id_reserva = r.id) AS total
              FROM mesa_pase p
              JOIN mesa_reserva r ON r.id = p.id_reserva
              JOIN mesa m ON m.id = r.id_mesa
              JOIN mesa_evento e ON e.id = r.id_evento
            """;

    private final JdbcTemplate jdbc;
    private final CredencialImagenService imagenes;
    private final PlantillaCredencialService plantillas;

    private record Pase(int numero, String codigo, String tipo, Long reservaId, String reserva, String titular,
                        String ci, String estado, Long eventoId, String mesa, String zona, String evento, int total,
                        String personaNombre, String personaCi, String personaFoto, String local, String logo) {
    }

    private static final long REPETIDA_MS = 15_000;

    public static boolean esCodigoDePase(String codigo) {
        return codigo != null && CODIGO_PASE.matcher(codigo).matches();
    }

    @Transactional
    public void generar(Long reservaId, String codigoReserva, int personas, String tipo) {
        jdbc.update("DELETE FROM mesa_pase WHERE id_reserva = ? AND ingreso_en IS NULL AND numero > ?", reservaId, personas);
        jdbc.update("UPDATE mesa_pase SET tipo = ? WHERE id_reserva = ? AND ingreso_en IS NULL", tipo, reservaId);
        Set<Integer> ocupados = new HashSet<>(
                jdbc.queryForList("SELECT numero FROM mesa_pase WHERE id_reserva = ?", Integer.class, reservaId));
        int faltan = personas - ocupados.size();
        for (int n = 1; faltan > 0; n++) {
            if (ocupados.contains(n)) continue;
            jdbc.update("""
                    INSERT INTO mesa_pase (id_reserva, numero, codigo, tipo) VALUES (?, ?, ?, ?)
                    ON CONFLICT (id_reserva, numero) DO NOTHING
                    """, reservaId, n, codigoReserva + "-" + n, tipo);
            faltan--;
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> deReserva(Long reservaId) {
        return jdbc.query("SELECT * FROM mesa_pase WHERE id_reserva = ? ORDER BY numero", (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("numero", rs.getInt("numero"));
            m.put("codigo", rs.getString("codigo"));
            m.put("tipo", rs.getString("tipo"));
            Timestamp t = rs.getTimestamp("ingreso_en");
            m.put("ingresoEn", t == null ? null : t.toLocalDateTime().toString());
            m.put("nombre", rs.getString("nombre"));
            m.put("ci", rs.getString("ci"));
            String foto = rs.getString("foto");
            m.put("fotoUrl", foto == null ? null : "/files/" + foto);
            m.put("dentro", rs.getBoolean("dentro"));
            return m;
        }, reservaId);
    }

    @Transactional
    public Map<String, Object> registrarIngreso(String codigo, Long usuarioId) {
        List<Map<String, Object>> f = jdbc.queryForList("""
                SELECT p.id, p.numero, p.tipo, p.ingreso_en, p.dentro, p.nombre, r.codigo AS reserva, r.estado, r.personas,
                       (SELECT a.cuando FROM mesa_pase_acceso a WHERE a.id_pase = p.id ORDER BY a.cuando DESC, a.id DESC LIMIT 1) AS ultimo
                  FROM mesa_pase p JOIN mesa_reserva r ON r.id = p.id_reserva
                 WHERE p.codigo = ?
                 FOR UPDATE OF p
                """, codigo);
        Map<String, Object> res = new LinkedHashMap<>();
        if (f.isEmpty()) {
            res.put("ok", false);
            res.put("mensaje", "Pase no válido");
            return res;
        }
        Map<String, Object> p = f.get(0);
        res.put("reserva", p.get("reserva"));
        res.put("pase", p.get("numero") + " de " + p.get("personas"));
        res.put("tipo", p.get("tipo"));
        if (!"CONFIRMADA".equals(p.get("estado"))) {
            res.put("ok", false);
            res.put("mensaje", "La reserva no está confirmada (" + p.get("estado") + ")");
            return res;
        }
        if (p.get("nombre") != null) res.put("persona", p.get("nombre"));
        boolean dentro = Boolean.TRUE.equals(p.get("dentro"));
        Timestamp ultimo = (Timestamp) p.get("ultimo");
        if (ultimo != null && System.currentTimeMillis() - ultimo.getTime() < REPETIDA_MS) {
            res.put("ok", false);
            res.put("sentido", dentro ? "E" : "S");
            res.put("mensaje", "Lectura repetida: este pase se registró hace unos segundos (" + (dentro ? "entrada" : "salida") + ")");
            return res;
        }
        String sentido = dentro ? "S" : "E";
        jdbc.update("UPDATE mesa_pase SET dentro = ?, ingreso_en = COALESCE(ingreso_en, CASE WHEN ? THEN now() END), "
                + "ingreso_por = COALESCE(ingreso_por, CASE WHEN ? THEN ? END) WHERE id = ?",
                !dentro, !dentro, !dentro, usuarioId, p.get("id"));
        jdbc.update("INSERT INTO mesa_pase_acceso (id_pase, sentido, registrado_por) VALUES (?, ?, ?)",
                p.get("id"), sentido, usuarioId);
        if (!dentro) {
            jdbc.update("UPDATE mesa_reserva SET ingreso_en = COALESCE(ingreso_en, now()), ingreso_por = COALESCE(ingreso_por, ?) "
                    + "WHERE codigo = ?", usuarioId, p.get("reserva"));
        }
        res.put("ok", true);
        res.put("sentido", sentido);
        res.put("mensaje", dentro ? "Salida registrada"
                : p.get("ingreso_en") == null ? "Ingreso registrado" : "Entrada registrada (vuelve a ingresar)");
        return res;
    }

    @Transactional(readOnly = true)
    public byte[] imagen(Long reservaId, int numero) {
        List<Pase> l = pases(" WHERE r.id = ? AND p.numero = ? AND r.estado = 'CONFIRMADA'", reservaId, numero);
        if (l.isEmpty()) return null;
        Pase p = l.get(0);
        return dibujar(p, plantillas.paraActividad(null, p.eventoId()));
    }

    @Transactional(readOnly = true)
    public byte[] pdf(String codigoReserva) {
        List<Pase> l = pases(" WHERE r.codigo = ? AND r.estado = 'CONFIRMADA' ORDER BY p.numero", codigoReserva);
        return l.isEmpty() ? null : hoja(l);
    }

    @Transactional(readOnly = true)
    public byte[] pdfDeReservas(List<Long> reservas) {
        if (reservas == null || reservas.isEmpty()) return null;
        List<Pase> l = pases(" WHERE r.id = ANY(?) AND r.estado = 'CONFIRMADA' ORDER BY m.codigo, r.id, p.numero",
                (Object) reservas.toArray(new Long[0]));
        return l.isEmpty() ? null : hoja(l);
    }

    @Transactional(readOnly = true)
    public byte[] recibo(Long reservaId) {
        List<Map<String, Object>> f = jdbc.queryForList("""
                SELECT r.*, m.codigo AS mesa, m.zona, m.capacidad, m.beneficios, e.nombre AS evento,
                       e.local_nombre, e.local_direccion, e.fecha_inicio, u.username AS validador,
                       (SELECT count(*) FROM mesa_pase p WHERE p.id_reserva = r.id) AS pases
                  FROM mesa_reserva r
                  JOIN mesa m ON m.id = r.id_mesa
                  JOIN mesa_evento e ON e.id = r.id_evento
                  LEFT JOIN usuario u ON u.id = r.validado_por
                 WHERE r.id = ?
                """, reservaId);
        if (f.isEmpty()) return null;
        Map<String, Object> d = f.get(0);
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A5, 30, 30, 30, 30);
            PdfWriter.getInstance(doc, salida);
            doc.open();
            Font marca = new Font(FontFamily.HELVETICA, 13, Font.BOLD, TINTA);
            Font sub = new Font(FontFamily.HELVETICA, 9, Font.BOLD, TINTA_SUAVE);
            Font seccion = new Font(FontFamily.HELVETICA, 7, Font.BOLD, TINTA_SUAVE);
            Font etiqueta = new Font(FontFamily.HELVETICA, 8, Font.BOLD, TINTA);
            Font normal = new Font(FontFamily.HELVETICA, 8.5f, Font.NORMAL, TINTA);
            Font chico = new Font(FontFamily.HELVETICA, 7, Font.NORMAL, TINTA_SUAVE);
            Font total = new Font(FontFamily.HELVETICA, 12, Font.BOLD, TINTA);

            PdfPTable cab = new PdfPTable(new float[] { 64, 36 });
            cab.setWidthPercentage(100);
            Paragraph izq = new Paragraph();
            izq.add(new Phrase("RECIBO DE RESERVA\n", marca));
            izq.add(new Phrase(texto(d.get("evento")) + "\n", sub));
            izq.add(new Phrase(texto(d.get("local_nombre"))
                    + (d.get("local_direccion") == null ? "" : " · " + d.get("local_direccion")), chico));
            cab.addCell(sinBorde(izq, Element.ALIGN_LEFT));
            Paragraph der = new Paragraph();
            der.add(new Phrase("N.º " + d.get("id") + "\n", etiqueta));
            der.add(new Phrase(texto(d.get("codigo")) + "\n", etiqueta));
            der.add(new Phrase(java.time.LocalDateTime.now().format(F_HORA), chico));
            cab.addCell(sinBorde(der, Element.ALIGN_RIGHT));
            doc.add(cab);
            doc.add(linea());

            String estado = texto(d.get("estado"));
            if (!"CONFIRMADA".equals(estado)) {
                Paragraph aviso = new Paragraph("RESERVA " + estado.replace('_', ' '),
                        new Font(FontFamily.HELVETICA, 10, Font.BOLD, new BaseColor(185, 28, 28)));
                aviso.setSpacingAfter(6);
                doc.add(aviso);
            }

            doc.add(rotulo("CLIENTE", seccion));
            PdfPTable cli = new PdfPTable(new float[] { 22, 78 });
            cli.setWidthPercentage(100);
            par(cli, "Titular", texto(d.get("nombre")) + " " + texto(d.get("apellidos")), etiqueta, normal);
            par(cli, "C.I.", texto(d.get("ci")), etiqueta, normal);
            par(cli, "Celular", texto(d.get("celular")), etiqueta, normal);
            if (d.get("correo") != null) par(cli, "Correo", texto(d.get("correo")), etiqueta, normal);
            doc.add(cli);

            doc.add(rotulo("DETALLE", seccion));
            PdfPTable det = new PdfPTable(new float[] { 22, 78 });
            det.setWidthPercentage(100);
            Timestamp inicio = (Timestamp) d.get("fecha_inicio");
            par(det, "Fecha", inicio == null ? "—" : inicio.toLocalDateTime().format(F_HORA), etiqueta, normal);
            par(det, "Mesa", texto(d.get("mesa")) + (d.get("zona") == null ? "" : " · " + d.get("zona")), etiqueta, normal);
            par(det, "Personas", texto(d.get("personas") == null ? d.get("capacidad") : d.get("personas"))
                    + " · " + d.get("pases") + " pase(s)" + (d.get("tipo_pase") == null ? "" : " " + d.get("tipo_pase")),
                    etiqueta, normal);
            if (d.get("beneficios") != null) par(det, "Incluye", texto(d.get("beneficios")), etiqueta, normal);
            par(det, "Pago", d.get("registrado_por") != null ? "En oficina"
                    : d.get("comprobante_archivo") != null ? "Comprobante adjunto" : "—", etiqueta, normal);
            Timestamp validado = (Timestamp) d.get("validado_en");
            par(det, "Validó", d.get("validador") == null ? "—"
                    : d.get("validador") + (validado == null ? "" : " · " + validado.toLocalDateTime().format(F_HORA)),
                    etiqueta, normal);
            doc.add(det);

            PdfPTable tot = new PdfPTable(new float[] { 70, 30 });
            tot.setWidthPercentage(100);
            tot.setSpacingBefore(8);
            PdfPCell t1 = new PdfPCell(new Phrase("TOTAL", total));
            t1.setBorder(Rectangle.NO_BORDER);
            t1.setBackgroundColor(GRIS_FONDO);
            t1.setPadding(6);
            tot.addCell(t1);
            PdfPCell t2 = new PdfPCell(new Phrase(bs((BigDecimal) d.get("monto")) + " Bs", total));
            t2.setBorder(Rectangle.NO_BORDER);
            t2.setBackgroundColor(GRIS_FONDO);
            t2.setPadding(6);
            t2.setHorizontalAlignment(Element.ALIGN_RIGHT);
            tot.addCell(t2);
            doc.add(tot);

            Image qr = new BarcodeQRCode(texto(d.get("codigo")), 140, 140, null).getImage();
            qr.scaleAbsolute(80, 80);
            qr.setAlignment(Element.ALIGN_CENTER);
            qr.setSpacingBefore(8);
            doc.add(qr);
            Paragraph pie = new Paragraph("Código de reserva " + d.get("codigo")
                    + ". Presenta tus pases en la puerta; cada pase permite un ingreso.", chico);
            pie.setAlignment(Element.ALIGN_CENTER);
            doc.add(pie);
            doc.close();
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el recibo: " + e.getMessage(), e);
        }
    }

    private List<Pase> pases(String filtro, Object... args) {
        return jdbc.query(SELECT_PASE + filtro, (rs, i) -> new Pase(rs.getInt("numero"), rs.getString("pase"),
                rs.getString("tipo"), rs.getLong("reserva_id"), rs.getString("reserva"),
                (rs.getString("nombre") + " " + rs.getString("apellidos")).trim(), rs.getString("ci"),
                rs.getString("estado"), rs.getLong("id_evento"), rs.getString("mesa"), rs.getString("zona"),
                rs.getString("evento"), rs.getInt("total"), rs.getString("p_nombre"), rs.getString("p_ci"),
                rs.getString("p_foto"), rs.getString("local_nombre"), rs.getString("logo_archivo")), args);
    }

    private byte[] dibujar(Pase p, PlantillaCredencial plantilla) {
        boolean conPersona = p.personaNombre() != null && !p.personaNombre().isBlank();
        String nombre = conPersona ? p.personaNombre() : p.numero() == 1 ? p.titular() : p.local();
        String ci = conPersona ? p.personaCi() : p.numero() == 1 ? p.ci() : null;
        boolean conFoto = p.personaFoto() != null && !p.personaFoto().isBlank();
        return imagenes.generarPase(plantilla, p.codigo(), nombre, p.evento(), ci, "Mesa " + p.mesa(),
                p.zona(), p.tipo(), conFoto ? p.personaFoto() : p.logo(), !conFoto);
    }

    @Transactional
    public String actualizarPersona(Long reservaId, int numero, String nombre, String ci, boolean quitarFoto) {
        String n = nombre == null || nombre.isBlank() ? null : nombre.trim().replaceAll("\\s+", " ");
        if (n != null && n.length() > 160) n = n.substring(0, 160);
        String c = ci == null || ci.isBlank() ? null : ci.trim();
        if (c != null && !c.matches("[0-9A-Za-z\\-]{3,20}")) return "Número de carnet no válido";
        int filas = jdbc.update("UPDATE mesa_pase SET nombre = ?, ci = ?, foto = CASE WHEN ? THEN NULL ELSE foto END "
                + "WHERE id_reserva = ? AND numero = ?", n, c, quitarFoto, reservaId, numero);
        return filas == 0 ? "El pase no existe" : null;
    }

    @Transactional
    public boolean guardarFoto(Long reservaId, int numero, String ruta) {
        return jdbc.update("UPDATE mesa_pase SET foto = ? WHERE id_reserva = ? AND numero = ?", ruta, reservaId, numero) > 0;
    }

    private byte[] hoja(List<Pase> lista) {
        Map<Long, PlantillaCredencial> porEvento = new LinkedHashMap<>();
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 24, 24, 24, 24);
            PdfWriter.getInstance(doc, salida);
            doc.open();
            Font chico = new Font(FontFamily.HELVETICA, 7.5f, Font.NORMAL, TINTA_SUAVE);
            PdfPTable tabla = new PdfPTable(2);
            tabla.setWidthPercentage(100);
            List<PdfPCell> celdas = new ArrayList<>();
            for (Pase p : lista) {
                PlantillaCredencial plantilla = porEvento.computeIfAbsent(p.eventoId(), id -> plantillas.paraActividad(null, id));
                Image img = Image.getInstance(dibujar(p, plantilla));
                img.setAlignment(Element.ALIGN_CENTER);
                PdfPCell celda = new PdfPCell();
                celda.setPadding(6);
                celda.setBorderColor(GRIS_LINEA);
                celda.setBorder(Rectangle.BOX);
                img.scaleToFit(260, 360);
                celda.addElement(img);
                Paragraph pie = new Paragraph("Mesa " + p.mesa() + " · pase " + p.numero() + " de " + p.total()
                        + " · " + p.codigo(), chico);
                pie.setAlignment(Element.ALIGN_CENTER);
                celda.addElement(pie);
                celdas.add(celda);
            }
            celdas.forEach(tabla::addCell);
            if (celdas.size() % 2 == 1) {
                PdfPCell vacia = new PdfPCell();
                vacia.setBorder(Rectangle.NO_BORDER);
                tabla.addCell(vacia);
            }
            doc.add(tabla);
            doc.close();
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudieron generar los pases: " + e.getMessage(), e);
        }
    }

    private static PdfPCell sinBorde(Paragraph p, int alineacion) {
        p.setAlignment(alineacion);
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.addElement(p);
        return c;
    }

    private static Paragraph rotulo(String texto, Font f) {
        Paragraph p = new Paragraph(texto, f);
        p.setSpacingBefore(6);
        p.setSpacingAfter(3);
        return p;
    }

    private static Paragraph linea() {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(2);
        com.itextpdf.text.pdf.draw.LineSeparator l = new com.itextpdf.text.pdf.draw.LineSeparator(0.6f, 100, GRIS_LINEA, Element.ALIGN_CENTER, -2);
        p.add(new com.itextpdf.text.Chunk(l));
        return p;
    }

    private static void par(PdfPTable t, String etiqueta, String valor, Font fe, Font fv) {
        PdfPCell a = new PdfPCell(new Phrase(etiqueta, fe));
        a.setBorder(Rectangle.BOTTOM);
        a.setBorderColor(GRIS_LINEA);
        a.setPadding(4);
        t.addCell(a);
        PdfPCell b = new PdfPCell(new Phrase(valor == null || valor.isBlank() ? "—" : valor, fv));
        b.setBorder(Rectangle.BOTTOM);
        b.setBorderColor(GRIS_LINEA);
        b.setPadding(4);
        t.addCell(b);
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

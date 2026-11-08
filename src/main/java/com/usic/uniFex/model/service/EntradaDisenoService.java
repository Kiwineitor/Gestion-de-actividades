package com.usic.uniFex.model.service;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.usic.uniFex.model.dto.PlantillaCredencial;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EntradaDisenoService {

    public static final int ANCHO = 1080;
    public static final int ALTO = 1920;
    public static final String PERSONALIZADA = "PERSONALIZADA";
    public static final String FONDO = "FONDO";
    public static final String SOLO_QR = "SOLO_QR";

    private record Tema(String clave, String nombre, Color inicio, Color fin, Color acento, Color textoAcento) {
    }

    private static final List<Tema> TEMAS = List.of(
            new Tema("NOCHE", "Noche", new Color(0x1E1B4B), new Color(0x7C3AED), new Color(0xF472B6), new Color(0x1E1B4B)),
            new Tema("FIESTA", "Fiesta", new Color(0xF97316), new Color(0xDB2777), new Color(0xFDE047), new Color(0x7C2D12)),
            new Tema("ELEGANTE", "Elegante", new Color(0x0B0B0F), new Color(0x2A2A31), new Color(0xD4AF37), new Color(0x0B0B0F)),
            new Tema("FRESCO", "Fresco", new Color(0x0F766E), new Color(0x0EA5E9), new Color(0xFEF08A), new Color(0x134E4A)));

    private final JdbcTemplate jdbc;
    private final FileStorageService archivos;
    private final PlantillaCredencialService plantillas;
    private final CredencialImagenService imagenes;
    private final ObjectMapper json = new ObjectMapper();

    @Value("${app.upload-root:uploads}")
    private String raiz;

    public record Diseno(String estilo, String fondo, boolean datos) {
    }

    public record Pase(String codigo, String nombre, String ci, String tipo, int numero, int total) {
    }

    private record Actividad(String nombre, String fechas, String lugar, String portada) {
    }

    @Transactional(readOnly = true)
    public Diseno disenoDe(Long edicion) {
        List<String> filas = jdbc.queryForList("SELECT entrada_diseno FROM edicion WHERE id = ?", String.class, edicion);
        String guardado = filas.isEmpty() ? null : filas.getFirst();
        String estilo = null;
        String fondo = null;
        boolean datos = true;
        if (guardado != null && !guardado.isBlank()) {
            try {
                Map<?, ?> m = json.readValue(guardado, Map.class);
                estilo = m.get("estilo") instanceof String s ? s : null;
                fondo = m.get("fondo") instanceof String s ? s : null;
                datos = !Boolean.FALSE.equals(m.get("datos"));
            } catch (IOException e) {
                estilo = null;
            }
        }
        if (!valido(estilo)) {
            estilo = plantillas.filaDeRol(edicion, PlantillaCredencialService.ROL_ENTRADA) != null ? PERSONALIZADA : "NOCHE";
        }
        if (FONDO.equals(estilo) && fondo == null) estilo = "NOCHE";
        return new Diseno(estilo, fondo, datos);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> describir(Long edicion) {
        Diseno d = disenoDe(edicion);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("estilo", d.estilo());
        m.put("datos", d.datos());
        m.put("fondoUrl", d.fondo() == null ? null : "/files/" + d.fondo());
        m.put("tienePersonalizada", plantillas.filaDeRol(edicion, PlantillaCredencialService.ROL_ENTRADA) != null);
        m.put("ancho", ANCHO);
        m.put("alto", ALTO);
        List<Map<String, Object>> estilos = new ArrayList<>();
        for (Tema t : TEMAS) {
            estilos.add(Map.of("clave", t.clave(), "nombre", t.nombre(), "desde", hex(t.inicio()), "hasta", hex(t.fin()), "acento", hex(t.acento())));
        }
        m.put("estilos", estilos);
        return m;
    }

    @Transactional
    public String guardar(Long edicion, String estilo, Boolean datos) {
        String e = estilo == null ? "" : estilo.trim().toUpperCase(Locale.ROOT);
        if (!valido(e)) return "Elige un diseño de la lista";
        Diseno actual = disenoDe(edicion);
        if (FONDO.equals(e) && actual.fondo() == null) return "Primero sube la imagen de fondo";
        escribir(edicion, e, actual.fondo(), datos == null ? actual.datos() : datos);
        return null;
    }

    @Transactional
    public String subirFondo(Long edicion, MultipartFile archivo) throws IOException {
        if (archivo == null || archivo.isEmpty()) return "Elige una imagen";
        String tipo = String.valueOf(archivo.getContentType());
        if (!List.of("image/png", "image/jpeg", "image/webp").contains(tipo)) return "La imagen debe ser PNG, JPG o WEBP";
        if (archivo.getSize() > 8L * 1024 * 1024) return "La imagen no puede pasar de 8 MB";
        BufferedImage prueba = ImageIO.read(archivo.getInputStream());
        if (prueba == null) return "No se pudo leer la imagen";
        String ruta = archivos.save(archivo, FileStorageService.Bucket.ACTIVIDADES, "fondo-entrada");
        escribir(edicion, FONDO, ruta, disenoDe(edicion).datos());
        return null;
    }

    private void escribir(Long edicion, String estilo, String fondo, boolean datos) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("estilo", estilo);
        if (fondo != null) m.put("fondo", fondo);
        m.put("datos", datos);
        try {
            jdbc.update("UPDATE edicion SET entrada_diseno = ? WHERE id = ?", json.writeValueAsString(m), edicion);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] dibujar(Long edicion, Pase pase) {
        return dibujar(edicion, disenoDe(edicion), pase);
    }

    @Transactional(readOnly = true)
    public byte[] vistaPrevia(Long edicion, String estilo, Boolean datos) {
        Diseno base = disenoDe(edicion);
        String e = estilo == null || !valido(estilo.toUpperCase(Locale.ROOT)) ? base.estilo() : estilo.toUpperCase(Locale.ROOT);
        if (FONDO.equals(e) && base.fondo() == null) e = "NOCHE";
        Diseno d = new Diseno(e, base.fondo(), datos == null ? base.datos() : datos);
        return dibujar(edicion, d, new Pase("ENT-MUESTRA1-1", "JUAN PÉREZ ROJAS", "1234567", "General", 1, 2));
    }

    private byte[] dibujar(Long edicion, Diseno d, Pase pase) {
        Actividad a = actividad(edicion);
        if (PERSONALIZADA.equals(d.estilo())) {
            PlantillaCredencial p = plantillas.paraEntradas(edicion);
            return imagenes.generarPase(p, pase.codigo(), pase.nombre(), a.nombre(), pase.ci(), pase.tipo(), a.fechas(), "ENTRADA");
        }
        BufferedImage lienzo = new BufferedImage(ANCHO, ALTO, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = lienzo.createGraphics();
        suavizar(g);
        if (SOLO_QR.equals(d.estilo())) soloQr(g, a, pase);
        else if (FONDO.equals(d.estilo())) conFondo(g, a, pase, d);
        else conTema(g, a, pase, tema(d.estilo()), d.datos());
        g.dispose();
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            ImageIO.write(lienzo, "png", salida);
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar la entrada", e);
        }
    }

    private void conTema(Graphics2D g, Actividad a, Pase p, Tema t, boolean datos) {
        g.setPaint(new GradientPaint(0, 0, t.inicio(), ANCHO, ALTO, t.fin()));
        g.fillRect(0, 0, ANCHO, ALTO);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.16f));
        g.setColor(t.acento());
        g.fill(new Ellipse2D.Double(ANCHO - 420, -260, 760, 760));
        g.fill(new Ellipse2D.Double(-300, ALTO - 520, 700, 700));
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f));
        g.fill(new Ellipse2D.Double(120, 520, 220, 220));
        g.setComposite(AlphaComposite.SrcOver);

        BufferedImage portada = leer(a.portada());
        if (portada != null) {
            cubrir(g, portada, 0, 0, ANCHO, 700);
            g.setPaint(new GradientPaint(0, 200, new Color(0, 0, 0, 0), 0, 700, mezclar(t.inicio(), t.fin())));
            g.fillRect(0, 0, ANCHO, 700);
        }

        g.setColor(t.acento());
        g.setFont(new Font("SansSerif", Font.BOLD, 34));
        centrado(g, "E N T R A D A", 150);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 88));
        int y = parrafo(g, a.nombre(), 250, ANCHO - 160, 3);
        g.setFont(new Font("SansSerif", Font.PLAIN, 40));
        g.setColor(new Color(255, 255, 255, 220));
        String sub = unir(a.fechas(), a.lugar());
        if (!sub.isEmpty()) parrafo(g, sub, y + 20, ANCHO - 200, 2);

        int cx = 90, cy = 760, cw = 900, ch = 1000;
        g.setColor(new Color(0, 0, 0, 60));
        g.fill(new RoundRectangle2D.Double(cx + 8, cy + 18, cw, ch, 64, 64));
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(cx, cy, cw, ch, 64, 64));
        int corte = cy + 790;
        g.setColor(mezclar(t.inicio(), t.fin()));
        g.fill(new Ellipse2D.Double(cx - 34, corte - 34, 68, 68));
        g.fill(new Ellipse2D.Double(cx + cw - 34, corte - 34, 68, 68));
        g.setColor(new Color(0xD1D5DB));
        g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, new float[] { 14, 16 }, 0));
        g.drawLine(cx + 60, corte, cx + cw - 60, corte);
        g.setStroke(new BasicStroke(1));

        qr(g, p.codigo(), (ANCHO - 640) / 2, cy + 50, 640);
        g.setColor(new Color(0x6B7280));
        g.setFont(new Font("Monospaced", Font.BOLD, 34));
        centrado(g, p.codigo(), cy + 745);

        int fila = corte + 80;
        String tipo = p.tipo() == null || p.tipo().isBlank() ? "Entrada" : p.tipo();
        g.setFont(new Font("SansSerif", Font.BOLD, 38));
        FontMetrics fm = g.getFontMetrics();
        String pastilla = recortar(g, tipo.toUpperCase(Locale.ROOT), 470);
        int pw = fm.stringWidth(pastilla) + 56;
        g.setColor(t.acento());
        g.fill(new RoundRectangle2D.Double(cx + 50, fila - 48, pw, 66, 66, 66));
        g.setColor(t.textoAcento());
        g.drawString(pastilla, cx + 78, fila);
        g.setColor(new Color(0x374151));
        g.setFont(new Font("SansSerif", Font.BOLD, 36));
        String n = "Entrada " + p.numero() + " de " + p.total();
        g.drawString(n, cx + cw - 50 - g.getFontMetrics().stringWidth(n), fila);
        if (datos && p.nombre() != null) {
            g.setColor(new Color(0x111827));
            g.setFont(new Font("SansSerif", Font.BOLD, 46));
            g.drawString(recortar(g, p.nombre(), cw - 100), cx + 50, fila + 100);
        }

        g.setColor(new Color(255, 255, 255, 200));
        g.setFont(new Font("SansSerif", Font.PLAIN, 32));
        centrado(g, "Muestra este QR en el ingreso · cada QR vale por una persona", ALTO - 90);
    }

    private void conFondo(Graphics2D g, Actividad a, Pase p, Diseno d) {
        BufferedImage fondo = leer(d.fondo());
        if (fondo != null) cubrir(g, fondo, 0, 0, ANCHO, ALTO);
        else {
            g.setColor(new Color(0x1E1B4B));
            g.fillRect(0, 0, ANCHO, ALTO);
        }
        if (!d.datos()) {
            int lado = 640, marco = 40, x = (ANCHO - lado) / 2 - marco, y = 1080;
            g.setColor(Color.WHITE);
            g.fill(new RoundRectangle2D.Double(x, y, lado + 2 * marco, lado + 2 * marco + 50, 48, 48));
            qr(g, p.codigo(), x + marco, y + marco, lado);
            g.setColor(new Color(0x6B7280));
            g.setFont(new Font("Monospaced", Font.BOLD, 30));
            centrado(g, p.codigo(), y + lado + marco + 50);
            return;
        }
        int cx = 90, cy = 1040, cw = 900, ch = 820;
        g.setColor(new Color(255, 255, 255, 240));
        g.fill(new RoundRectangle2D.Double(cx, cy, cw, ch, 56, 56));
        qr(g, p.codigo(), (ANCHO - 560) / 2, cy + 40, 560);
        g.setColor(new Color(0x6B7280));
        g.setFont(new Font("Monospaced", Font.BOLD, 30));
        centrado(g, p.codigo(), cy + 640);
        g.setColor(new Color(0x111827));
        g.setFont(new Font("SansSerif", Font.BOLD, 40));
        String linea = (p.tipo() == null || p.tipo().isBlank() ? "Entrada" : p.tipo()) + " · " + p.numero() + " de " + p.total();
        centrado(g, recortar(g, linea, cw - 80), cy + 710);
        if (p.nombre() != null) {
            g.setFont(new Font("SansSerif", Font.PLAIN, 38));
            centrado(g, recortar(g, p.nombre(), cw - 80), cy + 775);
        }
    }

    private void soloQr(Graphics2D g, Actividad a, Pase p) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, ANCHO, ALTO);
        g.setColor(new Color(0x111827));
        g.setFont(new Font("SansSerif", Font.BOLD, 56));
        parrafo(g, a.nombre(), 230, ANCHO - 160, 2);
        qr(g, p.codigo(), (ANCHO - 860) / 2, 480, 860);
        g.setColor(new Color(0x6B7280));
        g.setFont(new Font("Monospaced", Font.BOLD, 36));
        centrado(g, p.codigo(), 1420);
        g.setColor(new Color(0x111827));
        g.setFont(new Font("SansSerif", Font.BOLD, 42));
        centrado(g, (p.tipo() == null || p.tipo().isBlank() ? "Entrada" : p.tipo()) + " · " + p.numero() + " de " + p.total(), 1510);
    }

    private Actividad actividad(Long edicion) {
        List<Map<String, Object>> f = jdbc.queryForList(
                "SELECT nombre, fechas_texto, lugar, portada_archivo FROM edicion WHERE id = ?", edicion);
        if (f.isEmpty()) return new Actividad("Actividad", null, null, null);
        Map<String, Object> m = f.getFirst();
        return new Actividad((String) m.get("nombre"), (String) m.get("fechas_texto"), (String) m.get("lugar"), (String) m.get("portada_archivo"));
    }

    private BufferedImage leer(String relativa) {
        if (relativa == null || relativa.isBlank()) return null;
        try {
            Path base = Paths.get(raiz).toAbsolutePath().normalize();
            Path ruta = base.resolve(relativa).normalize();
            if (!ruta.startsWith(base) || !Files.isRegularFile(ruta)) return null;
            return ImageIO.read(ruta.toFile());
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void qr(Graphics2D g, String contenido, int x, int y, int lado) {
        BarcodeQRCode codigo = new BarcodeQRCode(contenido, lado, lado, null);
        java.awt.Image img = codigo.createAwtImage(Color.BLACK, Color.WHITE);
        Object antes = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setColor(Color.WHITE);
        g.fillRect(x, y, lado, lado);
        g.drawImage(img, x, y, lado, lado, null);
        if (antes != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, antes);
    }

    private static void cubrir(Graphics2D g, BufferedImage img, int x, int y, int w, int h) {
        double escala = Math.max((double) w / img.getWidth(), (double) h / img.getHeight());
        int iw = (int) Math.round(img.getWidth() * escala);
        int ih = (int) Math.round(img.getHeight() * escala);
        java.awt.Shape antes = g.getClip();
        g.setClip(x, y, w, h);
        g.drawImage(img, x + (w - iw) / 2, y + (h - ih) / 2, iw, ih, null);
        g.setClip(antes);
    }

    private static void suavizar(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static void centrado(Graphics2D g, String texto, int y) {
        if (texto == null) return;
        g.drawString(texto, (ANCHO - g.getFontMetrics().stringWidth(texto)) / 2, y);
    }

    private static int parrafo(Graphics2D g, String texto, int y, int ancho, int maxLineas) {
        if (texto == null || texto.isBlank()) return y;
        FontMetrics fm = g.getFontMetrics();
        List<String> lineas = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : texto.trim().split("\\s+")) {
            String prueba = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (fm.stringWidth(prueba) <= ancho || actual.isEmpty()) actual = new StringBuilder(prueba);
            else {
                lineas.add(actual.toString());
                actual = new StringBuilder(palabra);
            }
        }
        if (!actual.isEmpty()) lineas.add(actual.toString());
        if (lineas.size() > maxLineas) {
            List<String> cortas = new ArrayList<>(lineas.subList(0, maxLineas));
            cortas.set(maxLineas - 1, recortar(g, cortas.get(maxLineas - 1) + "…", ancho));
            lineas = cortas;
        }
        int alto = (int) (fm.getHeight() * 1.02);
        int linea = y + fm.getAscent();
        for (String l : lineas) {
            centrado(g, recortar(g, l, ancho), linea);
            linea += alto;
        }
        return linea - fm.getAscent() + 10;
    }

    private static String recortar(Graphics2D g, String texto, int ancho) {
        if (texto == null) return "";
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(texto) <= ancho) return texto;
        String t = texto;
        while (t.length() > 1 && fm.stringWidth(t + "…") > ancho) t = t.substring(0, t.length() - 1);
        return t.trim() + "…";
    }

    private static String unir(String a, String b) {
        StringBuilder s = new StringBuilder();
        if (a != null && !a.isBlank()) s.append(a.trim());
        if (b != null && !b.isBlank()) s.append(s.isEmpty() ? "" : " · ").append(b.trim());
        return s.toString();
    }

    private static Color mezclar(Color a, Color b) {
        return new Color((a.getRed() + b.getRed()) / 2, (a.getGreen() + b.getGreen()) / 2, (a.getBlue() + b.getBlue()) / 2);
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static Tema tema(String clave) {
        return TEMAS.stream().filter(t -> t.clave().equals(clave)).findFirst().orElse(TEMAS.getFirst());
    }

    private static boolean valido(String estilo) {
        if (estilo == null) return false;
        return PERSONALIZADA.equals(estilo) || FONDO.equals(estilo) || SOLO_QR.equals(estilo)
                || TEMAS.stream().anyMatch(t -> t.clave().equals(estilo));
    }
}

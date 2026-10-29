package com.usic.uniFex.model.service;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.itextpdf.text.pdf.BarcodeQRCode;
import com.usic.uniFex.model.dto.CredencialDTO;
import com.usic.uniFex.model.dto.PlantillaCredencial;
import com.usic.uniFex.model.dto.PlantillaCredencial.Caja;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CredencialImagenService {

    private final CredencialCodigoService codigos;
    private final ImagenPlantillaCredencial imagenes;

    @Value("${app.upload-root:uploads}")
    private String uploadRoot;

    private static final Color TINTA = new Color(0x1F, 0x24, 0x2B);

    public byte[] generar(CredencialDTO c, PlantillaCredencial plantilla, String urlBase) {
        try {
            log.info("[CREDENCIAL-WA] Generando credencial responsable={} inscripcion={} nombre='{}' fotoUrl='{}' plantilla={} urlBase={}",
                    c.responsableId(), c.inscripcionId(), c.nombre(), c.fotoUrl(), plantilla.id(), urlBase);
            BufferedImage fondo = leerPlantilla(plantilla.imagen());
            BufferedImage lienzo = new BufferedImage(
                    fondo.getWidth(), fondo.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = lienzo.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.drawImage(fondo, 0, 0, null);

            int w = lienzo.getWidth();
            int h = lienzo.getHeight();

            dibujarQr(g, plantilla.qr(), w, h, codigos.urlPublica(urlBase, c.codigo()));
            dibujarFoto(g, plantilla.foto(), w, h, c, plantilla.fotoCircular());

            texto(g, plantilla.nombre(), w, h, c.nombre(), plantilla, 2);
            texto(g, plantilla.empresa(), w, h, empresa(c), plantilla, 2);
            texto(g, plantilla.ci(), w, h, c.ci(), plantilla, 1);
            texto(g, plantilla.codigo(), w, h, c.casetas(), plantilla, 1);
            texto(g, plantilla.zona(), w, h, c.categoria(), plantilla, 1);
            texto(g, plantilla.tipo(), w, h, CredencialPdfService.tipoDe(c, plantilla), plantilla, 1);

            g.dispose();
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(lienzo, "png", salida);
            log.info("[CREDENCIAL-WA] Credencial generada responsable={} inscripcion={} bytes={}",
                    c.responsableId(), c.inscripcionId(), salida.size());
            return salida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar la credencial virtual", e);
        }
    }

    private static String empresa(CredencialDTO c) {
        String e = c.entidad() == null ? "" : c.entidad().trim();
        String r = c.rubro() == null ? "" : c.rubro().trim();
        return r.isEmpty() ? e : e + " / " + r;
    }

    private BufferedImage leerPlantilla(String recurso) throws IOException {
        try (InputStream in = new java.io.ByteArrayInputStream(imagenes.bytes(recurso))) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) throw new IOException("La plantilla no es una imagen legible: " + recurso);
            return img;
        }
    }

    private void dibujarQr(Graphics2D g, Caja caja, int w, int h, String contenido) {
        if (caja == null) return;
        int lado = (int) Math.round(caja.ancho() * w);
        BarcodeQRCode qr = new BarcodeQRCode(contenido, lado, lado, null);
        java.awt.Image img = qr.createAwtImage(Color.BLACK, Color.WHITE);
        int x = (int) Math.round(caja.x() * w);
        int y = (int) Math.round(caja.y() * h);
        g.setColor(Color.WHITE);
        g.fillRect(x, y, lado, lado);
        g.drawImage(img, x, y, lado, lado, null);
    }

    private void dibujarFoto(Graphics2D g, Caja caja, int w, int h, CredencialDTO c, boolean circular) {
        if (caja == null) {
            log.info("[CREDENCIAL-WA] Plantilla sin caja de foto responsable={} inscripcion={}",
                    c.responsableId(), c.inscripcionId());
            return;
        }
        int ladoHueco = (int) Math.round(caja.ancho() * w);
        int xh = (int) Math.round(caja.x() * w);
        int yh = (int) Math.round(caja.y() * h);

        BufferedImage foto = leerFoto(c.fotoUrl());
        if (foto == null) {
            log.warn("[CREDENCIAL-WA] No se pudo usar foto real; se dibuja silueta responsable={} inscripcion={} fotoUrl='{}' uploadRoot='{}'",
                    c.responsableId(), c.inscripcionId(), c.fotoUrl(), uploadRoot);
            siluetaSinFoto(g, xh, yh, ladoHueco);
            return;
        }

        log.info("[CREDENCIAL-WA] Foto real leida responsable={} inscripcion={} foto={}x{} hueco={}x{}",
                c.responsableId(), c.inscripcionId(), foto.getWidth(), foto.getHeight(), ladoHueco, ladoHueco);

        int lado = ladoHueco;
        int x = xh;
        int y = yh;

        BufferedImage redonda = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gf = redonda.createGraphics();
        gf.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gf.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (circular) gf.fill(new Ellipse2D.Float(0, 0, lado, lado));
        else gf.fillRect(0, 0, lado, lado);
        gf.setComposite(AlphaComposite.SrcIn);

        int corte = Math.min(foto.getWidth(), foto.getHeight());
        int cx = (foto.getWidth() - corte) / 2;
        int cy = (foto.getHeight() - corte) / 2;
        gf.drawImage(foto, 0, 0, lado, lado, cx, cy, cx + corte, cy + corte, null);
        gf.dispose();

        g.drawImage(redonda, x, y, null);
    }

    private void siluetaSinFoto(Graphics2D g, int x, int y, int lado) {
        g.setColor(new Color(0xE3, 0xE7, 0xEC));
        g.fill(new Ellipse2D.Float(x, y, lado, lado));

        g.setColor(new Color(0xA8, 0xB2, 0xBF));
        float dCabeza = lado * 0.34f;
        g.fill(new Ellipse2D.Float(x + (lado - dCabeza) / 2f, y + lado * 0.17f, dCabeza, dCabeza));
        java.awt.Shape antes = g.getClip();
        g.setClip(new Ellipse2D.Float(x, y, lado, lado));
        float anchoH = lado * 0.62f;
        g.fill(new Ellipse2D.Float(x + (lado - anchoH) / 2f, y + lado * 0.58f, anchoH, lado * 0.55f));
        g.setClip(antes);
    }

    private BufferedImage leerFoto(String fotoUrl) {
        if (fotoUrl == null || fotoUrl.isBlank()) {
            log.warn("[CREDENCIAL-WA] fotoUrl vacio; no hay archivo que leer");
            return null;
        }
        String relativa = fotoUrl.startsWith("/files/") ? fotoUrl.substring("/files/".length()) : fotoUrl;
        try {
            Path base = Paths.get(uploadRoot).toAbsolutePath().normalize();
            Path destino = base.resolve(relativa).normalize();
            log.info("[CREDENCIAL-WA] Resolviendo foto fotoUrl='{}' relativa='{}' base='{}' destino='{}'",
                    fotoUrl, relativa, base, destino);
            if (!destino.startsWith(base)) {
                log.warn("[CREDENCIAL-WA] Foto rechazada por salir de uploadRoot destino='{}' base='{}'", destino, base);
                return null;
            }
            if (!Files.isRegularFile(destino)) {
                log.warn("[CREDENCIAL-WA] Archivo de foto no existe o no es archivo regular destino='{}'", destino);
                return null;
            }
            BufferedImage img = ImageIO.read(destino.toFile());
            if (img == null) {
                log.warn("[CREDENCIAL-WA] ImageIO no pudo decodificar la foto destino='{}'", destino);
            }
            return img;
        } catch (IOException | RuntimeException e) {
            log.warn("[CREDENCIAL-WA] No se pudo leer la foto {}: {}", fotoUrl, e.getMessage());
            return null;
        }
    }

    private static final double TAM_BASE = 0.026;

    private void texto(Graphics2D g, Caja caja, int w, int h, String valor,
                       PlantillaCredencial p, int maxLineas) {
        if (caja == null || valor == null || valor.isBlank()) return;
        String v = p.mayusculas() ? valor.trim().toUpperCase() : valor.trim();

        int x = (int) Math.round(caja.x() * w);
        int y = (int) Math.round(caja.y() * h);
        int ancho = (int) Math.round(caja.ancho() * w);
        int alto = (int) Math.round(caja.alto() * h);

        int tam = (int) Math.round(h * TAM_BASE);
        Font fuente = new Font(Font.SANS_SERIF, Font.BOLD, tam);
        java.util.List<String> lineas = partir(g, v, fuente, ancho, maxLineas);

        while (tam > 12 && !cabe(g, lineas, fuente, ancho)) {
            tam -= 2;
            fuente = new Font(Font.SANS_SERIF, Font.BOLD, tam);
            lineas = partir(g, v, fuente, ancho, maxLineas);
        }

        g.setFont(fuente);
        g.setColor(CredencialPdfService.colorDe(p.colorTexto(), TINTA));
        java.awt.FontMetrics fm = g.getFontMetrics(fuente);
        int salto = (int) Math.round(fm.getHeight() * 0.92);
        int altoTexto = fm.getAscent() - fm.getDescent() + salto * (lineas.size() - 1);
        int baseY = y + (alto + altoTexto) / 2 - salto * (lineas.size() - 1);

        for (String linea : lineas) {
            int baseX = p.centrado() ? x + (ancho - fm.stringWidth(linea)) / 2 : x;
            g.drawString(linea, baseX, baseY);
            baseY += salto;
        }
    }

    private java.util.List<String> partir(Graphics2D g, String v, Font f, int ancho, int maxLineas) {
        java.awt.FontMetrics fm = g.getFontMetrics(f);
        if (maxLineas <= 1 || fm.stringWidth(v) <= ancho) return java.util.List.of(v);

        java.util.List<String> lineas = new java.util.ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : v.split("\\s+")) {
            String tentativa = actual.isEmpty() ? palabra : actual + " " + palabra;
            if (fm.stringWidth(tentativa) <= ancho || actual.isEmpty()) {
                actual.setLength(0);
                actual.append(tentativa);
            } else if (lineas.size() + 1 < maxLineas) {
                lineas.add(actual.toString());
                actual.setLength(0);
                actual.append(palabra);
            } else {
                actual.append(' ').append(palabra);
            }
        }
        lineas.add(actual.toString());
        return lineas;
    }

    private boolean cabe(Graphics2D g, java.util.List<String> lineas, Font f, int ancho) {
        java.awt.FontMetrics fm = g.getFontMetrics(f);
        return lineas.stream().allMatch(l -> fm.stringWidth(l) <= ancho);
    }
}

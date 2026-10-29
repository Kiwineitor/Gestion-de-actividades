package com.usic.uniFex.model.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;
import com.usic.uniFex.model.dto.ApoyoCredencialDTO;
import com.usic.uniFex.model.dto.CredencialDTO;
import com.usic.uniFex.model.dto.PlantillaCredencial;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApoyoCredencialPdfService {

    private static final float CM = 72f / 2.54f;
    private static final String PLANTILLA = "static/assets/CREDENCIAL_APOYO.png";
    private static final String REVERSO = "static/assets/CREDENCIAL_REVERSO.png";
    private static final String PLANTILLA_FUL = "static/assets/CREDENCIAL_FUL.png";
    private static final String PLANTILLA_PRENSA = "static/assets/CREDENCIAL_PRENSA.png";
    private static final String PLANTILLA_PRENSA_NUEVO = "static/assets/CREDENCIAL_PRENSA_NUEVO.png";
    private static final float ANCHO = 10 * CM;
    private static final float A4_ANCHO = 21 * CM;
    private static final float A4_ALTO = 29.7f * CM;
    private static final int GRUPO_DUPLEX = 4;

    private static final String DEPENDENCIA_FUL = "Federación Universitaria Local";
    private static final String DEPENDENCIA_PRENSA = "Prensa";

    private record Disposicion(String fondo, double[] nombre, double[] dependencia,
                               double[] ci, double avatarCx, double avatarCy,
                               double avatarRadio, double qrX, double qrY, double qrLado) {
    }

    private static final Disposicion NORMAL = new Disposicion(PLANTILLA,
            new double[]{0.105, 0.329, 0.755, 0.072},
            new double[]{0.105, 0.410, 0.755, 0.088},
            new double[]{0.105, 0.508, 0.755, 0.070},
            0.500, 0.2085, 0.134,
            0.370, 0.620, 0.260);

    private static final Disposicion FUL = new Disposicion(PLANTILLA_FUL,
            new double[]{0.105, 0.336, 0.760, 0.094},
            new double[]{0.105, 0.437, 0.760, 0.092},
            new double[]{0.105, 0.533, 0.760, 0.061},
            0.502, 0.2135, 0.134,
            0.370, 0.620, 0.260);

    private static final Disposicion PRENSA = new Disposicion(PLANTILLA_PRENSA,
            new double[]{0.105, 0.336, 0.760, 0.090},
            new double[]{0.105, 0.435, 0.760, 0.083},
            new double[]{0.105, 0.527, 0.760, 0.065},
            0.500, 0.2130, 0.134,
            0.370, 0.620, 0.260);

    private static final Disposicion PRENSA_NUEVO = new Disposicion(PLANTILLA_PRENSA_NUEVO,
            new double[]{196.0 / 1102, 728.0 / 1427, 817.0 / 1102, 112.0 / 1427},
            null,
            null,
            0, 0, 0,
            414.0 / 1102, 861.0 / 1427, 274.0 / 1102);

    private static final double MARGEN_IZQ_PRIMERAS = 0.025;

    private static final BaseColor TINTA = new BaseColor(25, 25, 25);

    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();

    private final PlantillaCredencialService plantillas;
    private final CredencialPdfService credencialPdf;
    private final CredencialCodigoService codigosCredencial;

    private PlantillaCredencial plantillaDe(ApoyoCredencialDTO c) {
        return plantillas.paraApoyo(c.idDependencia());
    }

    private static CredencialDTO comoCredencial(ApoyoCredencialDTO c) {
        boolean conFoto = c.fotoUrl() != null && !c.fotoUrl().isBlank();
        return new CredencialDTO(c.id(), c.codigo(), c.nombreCompleto(), c.ci(), c.fotoUrl(), false,
                c.dependenciaNombre(), c.descripcionTarea(), null, null, null, null, true, conFoto,
                false, null, null, true, c.rol());
    }

    private Image reversoDe(ApoyoCredencialDTO c, Image porDefecto) throws Exception {
        PlantillaCredencial p = plantillaDe(c);
        return p == null || p.imagenReverso() == null ? porDefecto
                : Image.getInstance(credencialPdf.imagen(p.imagenReverso()));
    }

    @Value("${app.upload-root:uploads}")
    private String uploadRoot;

    public byte[] generar(List<ApoyoCredencialDTO> credenciales, String urlBase,
                          ApoyoCodigoService codigos) {
        if (credenciales == null || credenciales.isEmpty()) {
            throw new IllegalArgumentException("No hay credenciales que imprimir");
        }
        try {
            Image fondoReverso = Image.getInstance(bytes(REVERSO));
            Image muestra = Image.getInstance(bytes(PLANTILLA));
            float alto = ANCHO * muestra.getHeight() / muestra.getWidth();
            Document doc = new Document(new Rectangle(ANCHO, alto), 0, 0, 0, 0);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            doc.open();
            PdfContentByte lienzo = writer.getDirectContent();
            BaseFont fuente = BaseFont.createFont(
                    BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            for (int i = 0; i < credenciales.size(); i++) {
                if (i > 0) doc.newPage();
                pintar(lienzo, fuente, credenciales.get(i), urlBase, codigos);
                doc.newPage();
                pintarReverso(lienzo, reversoDe(credenciales.get(i), fondoReverso));
            }
            doc.close();
            log.info("Credenciales de apoyo impresas: {}", credenciales.size());
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF de apoyo: "
                    + e.getMessage(), e);
        }
    }

    static Disposicion disposicionDe(ApoyoCredencialDTO c) {
        String dep = c.dependenciaNombre() == null ? "" : c.dependenciaNombre().trim();
        if (DEPENDENCIA_FUL.equalsIgnoreCase(dep)) return FUL;
        if (DEPENDENCIA_PRENSA.equalsIgnoreCase(dep)) return PRENSA_NUEVO;
        return NORMAL;
    }

    private void pintar(PdfContentByte lienzo, BaseFont fuente,
                        ApoyoCredencialDTO c, String urlBase, ApoyoCodigoService codigos)
            throws Exception {
        PlantillaCredencial propia = plantillaDe(c);
        if (propia != null) {
            Image muestra = Image.getInstance(bytes(PLANTILLA));
            float hoja = ANCHO * muestra.getHeight() / muestra.getWidth();
            float alto = (float) (ANCHO * credencialPdf.proporcion(propia));
            float escala = Math.min(1f, hoja / alto);
            float w = ANCHO * escala;
            float h = alto * escala;
            credencialPdf.pintarFrente(lienzo, propia, comoCredencial(c), (ANCHO - w) / 2f, (hoja - h) / 2f,
                    w, h, urlBase, codigosCredencial);
            return;
        }
        Disposicion d = disposicionDe(c);
        Image fondoOriginal = Image.getInstance(bytes(d.fondo()));
        float w = ANCHO;
        float h = w * fondoOriginal.getHeight() / fondoOriginal.getWidth();

        Image fondo = Image.getInstance(fondoOriginal);
        fondo.scaleAbsolute(w, h);
        fondo.setAbsolutePosition(0, 0);
        lienzo.addImage(fondo);

        textoEnCaja(lienzo, fuente, d.nombre(), mayus(c.nombreCompleto()), w, h,
                MARGEN_IZQ_PRIMERAS);
        if (d.dependencia() != null) {
            textoEnCaja(lienzo, fuente, d.dependencia(), mayus(dependenciaDe(c)), w, h,
                    MARGEN_IZQ_PRIMERAS);
        }
        if (d.ci() != null) {
            textoEnCaja(lienzo, fuente, d.ci(), "C.I. " + orVacio(c.ci()), w, h, 0);
        }

        if (d.avatarRadio() > 0) {
            dibujarFotoCircular(lienzo, c, d, w, h);
        }

        float lado = (float) (d.qrLado() * w);
        float qrX = (float) (d.qrX() * w);
        float qrY = h - (float) (d.qrY() * h) - lado;
        BarcodeQRCode qr = new BarcodeQRCode(
                codigos.urlPublica(urlBase, c.codigo()), 1000, 1000, null);
        Image imgQr = qr.getImage();
        imgQr.scaleAbsolute(lado, lado);
        imgQr.setAbsolutePosition(qrX, qrY);
        lienzo.addImage(imgQr);
    }

    private void pintarReverso(PdfContentByte lienzo, Image fondoReverso) throws Exception {
        Image fondo = Image.getInstance(fondoReverso);
        fondo.scaleAbsolute(ANCHO, ANCHO * fondo.getHeight() / fondo.getWidth());
        fondo.setAbsolutePosition(0, 0);
        lienzo.addImage(fondo);
    }

    private void dibujarFotoCircular(PdfContentByte lienzo, ApoyoCredencialDTO c,
                                     Disposicion d, float w, float h) {
        if (c.fotoUrl() == null || c.fotoUrl().isBlank()) return;
        Path ruta = rutaFoto(c.fotoUrl());
        if (ruta == null) return;
        try {
            Image foto = Image.getInstance(ruta.toAbsolutePath().toString());
            float cx = (float) (d.avatarCx() * w);
            float cy = h - (float) (d.avatarCy() * h);
            float radio = (float) (d.avatarRadio() * w);

            float s = Math.max(2 * radio / foto.getWidth(), 2 * radio / foto.getHeight());
            float fw = foto.getWidth() * s;
            float fh = foto.getHeight() * s;
            foto.scaleAbsolute(fw, fh);
            foto.setAbsolutePosition(cx - fw / 2f, cy - fh / 2f);

            lienzo.saveState();
            lienzo.circle(cx, cy, radio);
            lienzo.clip();
            lienzo.newPath();
            lienzo.addImage(foto);
            lienzo.restoreState();
        } catch (Exception e) {
            log.warn("Foto ilegible en la credencial de apoyo {} ({}): {}. Sale sin ella.",
                    c.nombreCompleto(), c.fotoUrl(), e.getMessage());
        }
    }

    private Path rutaFoto(String fotoUrl) {
        String relativa = fotoUrl.startsWith("/files/")
                ? fotoUrl.substring("/files/".length()) : fotoUrl;
        try {
            Path base = java.nio.file.Paths.get(uploadRoot).toAbsolutePath().normalize();
            Path destino = base.resolve(relativa).normalize();
            if (!destino.startsWith(base) || !java.nio.file.Files.isRegularFile(destino)) return null;
            return destino;
        } catch (RuntimeException e) {
            log.warn("No se pudo resolver la foto {}: {}", fotoUrl, e.getMessage());
            return null;
        }
    }

    private static String dependenciaDe(ApoyoCredencialDTO c) {
        return c.dependenciaNombre() == null ? "" : c.dependenciaNombre().trim();
    }

    private void textoEnCaja(PdfContentByte lienzo, BaseFont fuente, double[] caja,
                             String contenido, float w, float h, double margenIzq) {
        if (contenido == null || contenido.isBlank()) return;
        float aire = (float) (margenIzq * w);
        float cajaW = (float) (caja[2] * w) - aire;
        float cajaH = (float) (caja[3] * h);
        float x = (float) (caja[0] * w) + aire;
        float y = h - (float) (caja[1] * h) - cajaH;

        java.util.List<String> lineas = new java.util.ArrayList<>(java.util.List.of(contenido));
        float anchoUtil = cajaW * 0.96f;
        float tam = ajustar(fuente, contenido, anchoUtil, cajaH * 0.62f, cajaH * 0.28f);
        if (fuente.getWidthPoint(contenido, tam) > anchoUtil) {
            lineas = partirEnDos(contenido);
            tam = Float.MAX_VALUE;
            for (String l : lineas) {
                tam = Math.min(tam, ajustar(fuente, l, anchoUtil, cajaH * 0.31f, cajaH * 0.14f));
            }
            for (int i = 0; i < lineas.size(); i++) {
                String l = lineas.get(i);
                if (fuente.getWidthPoint(l, tam) > anchoUtil) {
                    lineas.set(i, recortar(l, anchoUtil, fuente, tam));
                }
            }
        }

        float altoLinea = tam * 1.15f;
        float yTexto = y + (cajaH + (lineas.size() - 1) * altoLinea - tam * 0.70f) / 2f;
        for (int i = lineas.size() - 1; i >= 0; i--) {
            lienzo.beginText();
            lienzo.setFontAndSize(fuente, tam);
            lienzo.setColorFill(TINTA);
            lienzo.showTextAligned(Element.ALIGN_CENTER, lineas.get(i),
                    x + cajaW / 2f, yTexto, 0);
            lienzo.endText();
            yTexto -= altoLinea;
        }
    }

    private static float ajustar(BaseFont fuente, String texto, float ancho,
                                 float maximo, float minimo) {
        float t = maximo;
        while (t > minimo && fuente.getWidthPoint(texto, t) > ancho) t -= 0.5f;
        return t;
    }

    private static java.util.List<String> partirEnDos(String s) {
        String[] palabras = s.trim().split("\\s+");
        if (palabras.length < 2) return new java.util.ArrayList<>(java.util.List.of(s));
        int mejor = 1;
        int mejorMax = Integer.MAX_VALUE;
        for (int i = 1; i < palabras.length; i++) {
            String a = String.join(" ", java.util.Arrays.copyOfRange(palabras, 0, i));
            String b = String.join(" ", java.util.Arrays.copyOfRange(palabras, i, palabras.length));
            int m = Math.max(a.length(), b.length());
            if (m < mejorMax) {
                mejorMax = m;
                mejor = i;
            }
        }
        var lineas = new java.util.ArrayList<String>();
        lineas.add(String.join(" ", java.util.Arrays.copyOfRange(palabras, 0, mejor)));
        lineas.add(String.join(" ", java.util.Arrays.copyOfRange(palabras, mejor, palabras.length)));
        return lineas;
    }

    private static String recortar(String s, float cajaW, BaseFont fuente, float tam) {
        String p = s;
        while (p.length() > 4 && fuente.getWidthPoint(p + "…", tam) > cajaW) {
            p = p.substring(0, p.length() - 1);
        }
        return p + "…";
    }

    private static String mayus(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }

    private static String orVacio(String s) {
        return s == null ? "" : s.trim();
    }

    private byte[] bytes(String recurso) {
        return cache.computeIfAbsent(recurso, r -> {
            try (InputStream in = new org.springframework.core.io.ClassPathResource(r)
                    .getInputStream()) {
                return in.readAllBytes();
            } catch (Exception e) {
                throw new IllegalStateException(
                        "No se encontro la plantilla de apoyo: " + r, e);
            }
        });
    }

    public byte[] generarDuplex4(List<ApoyoCredencialDTO> credenciales, String urlBase,
                                 ApoyoCodigoService codigos) {
        if (credenciales == null || credenciales.isEmpty()) {
            throw new IllegalArgumentException("No hay credenciales que imprimir");
        }
        try {
            Image reversoImg = Image.getInstance(bytes(REVERSO));
            Rectangle hoja = new Rectangle(A4_ANCHO, A4_ALTO);
            float w = ANCHO;
            float h = ANCHO * 13f / 10f;
            validarCuadricula(hoja, w, h);

            float x0 = (hoja.getWidth() - 2 * w) / 2;
            float y0 = (hoja.getHeight() - 2 * h) / 2;

            Document doc = new Document(hoja, 0, 0, 0, 0);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            preferenciaTamanoReal(writer);
            doc.open();
            PdfContentByte lienzo = writer.getDirectContent();
            BaseFont fuente = BaseFont.createFont(
                    BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);

            com.itextpdf.text.pdf.PdfTemplate tplReverso = plantillaReverso(lienzo, reversoImg, w, h);

            int totalGrupos = (credenciales.size() + GRUPO_DUPLEX - 1) / GRUPO_DUPLEX;
            for (int g = 0; g < totalGrupos; g++) {
                List<ApoyoCredencialDTO> grupo = credenciales.subList(
                        g * GRUPO_DUPLEX, Math.min(credenciales.size(), (g + 1) * GRUPO_DUPLEX));

                if (g > 0) doc.newPage();

                com.itextpdf.text.pdf.PdfTemplate[] tplsFrente = new com.itextpdf.text.pdf.PdfTemplate[grupo.size()];
                com.itextpdf.text.pdf.PdfTemplate[] tplsReverso = new com.itextpdf.text.pdf.PdfTemplate[grupo.size()];
                for (int i = 0; i < grupo.size(); i++) {
                    PlantillaCredencial propia = plantillaDe(grupo.get(i));
                    Image frenteImg = propia != null
                            ? Image.getInstance(credencialPdf.imagen(propia.imagen()))
                            : Image.getInstance(bytes(disposicionDe(grupo.get(i)).fondo()));
                    tplsFrente[i] = plantillaFrente(lienzo, frenteImg, w, h);
                    tplsReverso[i] = propia == null || propia.imagenReverso() == null ? tplReverso
                            : plantillaReverso(lienzo, Image.getInstance(credencialPdf.imagen(propia.imagenReverso())), w, h);
                }

                pintarGrupoFrentes(lienzo, fuente, tplsFrente, grupo, x0, y0, w, h, urlBase, codigos);
                doc.newPage();

                pintarGrupoReversos(lienzo, tplsReverso, x0, y0, w, h);
            }
            doc.close();
            log.info("Credenciales de apoyo duplex 2x2: {}, hojas: {}", credenciales.size(), 2 * totalGrupos);
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF duplex de apoyo: "
                    + e.getMessage(), e);
        }
    }

    private void preferenciaTamanoReal(PdfWriter writer) {
        writer.addViewerPreference(com.itextpdf.text.pdf.PdfName.PRINTSCALING,
                com.itextpdf.text.pdf.PdfName.NONE);
        writer.addViewerPreference(com.itextpdf.text.pdf.PdfName.DUPLEX,
                com.itextpdf.text.pdf.PdfName.DUPLEXFLIPLONGEDGE);
    }

    private void validarCuadricula(Rectangle hoja, float w, float h) {
        if (2 * w > hoja.getWidth() || 2 * h > hoja.getHeight()) {
            throw new IllegalArgumentException("Cuatro credenciales de ese tamano no caben en la hoja A4");
        }
    }

    private com.itextpdf.text.pdf.PdfTemplate plantillaFrente(
            PdfContentByte lienzo, Image frente, float w, float h) throws Exception {
        com.itextpdf.text.pdf.PdfTemplate tpl = lienzo.createTemplate(w, h);
        Image f = Image.getInstance(frente);
        f.scaleAbsolute(w, h);
        f.setAbsolutePosition(0, 0);
        tpl.addImage(f);
        return tpl;
    }

    private com.itextpdf.text.pdf.PdfTemplate plantillaReverso(
            PdfContentByte lienzo, Image reverso, float w, float h) throws Exception {
        float escala = Math.max(w / reverso.getWidth(), h / reverso.getHeight());
        float ancho = reverso.getWidth() * escala;
        float alto = reverso.getHeight() * escala;
        Image fondo = Image.getInstance(reverso);
        fondo.scaleAbsolute(ancho, alto);
        fondo.setAbsolutePosition((w - ancho) / 2f, (h - alto) / 2f);
        com.itextpdf.text.pdf.PdfTemplate tpl = lienzo.createTemplate(w, h);
        tpl.saveState();
        try {
            tpl.rectangle(0, 0, w, h);
            tpl.clip();
            tpl.newPath();
            tpl.addImage(fondo);
        } finally {
            tpl.restoreState();
        }
        return tpl;
    }

    private void pintarGrupoFrentes(PdfContentByte lienzo, BaseFont fuente,
                                    com.itextpdf.text.pdf.PdfTemplate[] tplsFrente,
                                    List<ApoyoCredencialDTO> grupo,
                                    float x0, float y0, float w, float h,
                                    String urlBase, ApoyoCodigoService codigos) throws Exception {
        for (int i = 0; i < grupo.size(); i++) {
            int col = i % 2;
            boolean arriba = i < 2;
            float x = x0 + col * w;
            float y = arriba ? y0 + h : y0;

            lienzo.addTemplate(tplsFrente[i], x, y);

            PlantillaCredencial propia = plantillaDe(grupo.get(i));
            if (propia != null) {
                credencialPdf.pintarSoloContenido(lienzo, propia, comoCredencial(grupo.get(i)), x, y, w, h,
                        urlBase, codigosCredencial);
            } else {
                pintarContenidoEnHueco(lienzo, fuente, grupo.get(i), x, y, w, h, urlBase, codigos);
            }

            marcasDeCorte(lienzo, x, y, w, h);
        }
        lineaDeCorte(lienzo, x0 + w, y0, y0 + 2 * h, false);
        lineaDeCorte(lienzo, y0 + h, x0, x0 + 2 * w, true);
    }

    private void pintarGrupoReversos(PdfContentByte lienzo,
                                     com.itextpdf.text.pdf.PdfTemplate[] tplsReverso,
                                     float x0, float y0, float w, float h) throws Exception {
        for (int i = 0; i < tplsReverso.length; i++) {
            com.itextpdf.text.pdf.PdfTemplate tplReverso = tplsReverso[i];
            int col = 1 - (i % 2);
            boolean arriba = i < 2;
            float x = x0 + col * w;
            float y = arriba ? y0 + h : y0;
            lienzo.addTemplate(tplReverso, x, y);
        }
    }

    private void pintarContenidoEnHueco(PdfContentByte lienzo, BaseFont fuente,
                                        ApoyoCredencialDTO c, float x0, float y0,
                                        float w, float h, String urlBase, ApoyoCodigoService codigos)
            throws Exception {
        Disposicion d = disposicionDe(c);

        textoEnCaja(lienzo, fuente, d.nombre(), mayus(c.nombreCompleto()), w, h,
                MARGEN_IZQ_PRIMERAS, x0, y0);

        if (d.dependencia() != null) {
            textoEnCaja(lienzo, fuente, d.dependencia(), mayus(dependenciaDe(c)), w, h,
                    MARGEN_IZQ_PRIMERAS, x0, y0);
        }
        if (d.ci() != null) {
            textoEnCaja(lienzo, fuente, d.ci(), "C.I. " + orVacio(c.ci()), w, h, 0, x0, y0);
        }

        if (d.avatarRadio() > 0) {
            dibujarFotoCircular(lienzo, c, d, w, h, x0, y0);
        }

        float lado = (float) (d.qrLado() * w);
        float qrX = x0 + (float) (d.qrX() * w);
        float qrY = y0 + h - (float) (d.qrY() * h) - lado;
        BarcodeQRCode qr = new BarcodeQRCode(
                codigos.urlPublica(urlBase, c.codigo()), 1000, 1000, null);
        Image imgQr = qr.getImage();
        imgQr.scaleAbsolute(lado, lado);
        imgQr.setAbsolutePosition(qrX, qrY);
        lienzo.addImage(imgQr);
    }

    private void textoEnCaja(PdfContentByte lienzo, BaseFont fuente, double[] caja,
                             String contenido, float w, float h, double margenIzq,
                             float x0, float y0) {
        if (contenido == null || contenido.isBlank()) return;
        float aire = (float) (margenIzq * w);
        float cajaW = (float) (caja[2] * w) - aire;
        float cajaH = (float) (caja[3] * h);
        float x = x0 + (float) (caja[0] * w) + aire;
        float y = y0 + h - (float) (caja[1] * h) - cajaH;

        java.util.List<String> lineas = new java.util.ArrayList<>(java.util.List.of(contenido));
        float anchoUtil = cajaW * 0.96f;
        float tam = ajustar(fuente, contenido, anchoUtil, cajaH * 0.62f, cajaH * 0.28f);
        if (fuente.getWidthPoint(contenido, tam) > anchoUtil) {
            lineas = partirEnDos(contenido);
            tam = Float.MAX_VALUE;
            for (String l : lineas) {
                tam = Math.min(tam, ajustar(fuente, l, anchoUtil, cajaH * 0.31f, cajaH * 0.14f));
            }
            for (int i = 0; i < lineas.size(); i++) {
                String l = lineas.get(i);
                if (fuente.getWidthPoint(l, tam) > anchoUtil) {
                    lineas.set(i, recortar(l, anchoUtil, fuente, tam));
                }
            }
        }

        float altoLinea = tam * 1.15f;
        float yTexto = y + (cajaH + (lineas.size() - 1) * altoLinea - tam * 0.70f) / 2f;
        for (int i = lineas.size() - 1; i >= 0; i--) {
            lienzo.beginText();
            lienzo.setFontAndSize(fuente, tam);
            lienzo.setColorFill(TINTA);
            lienzo.showTextAligned(Element.ALIGN_CENTER, lineas.get(i),
                    x + cajaW / 2f, yTexto, 0);
            lienzo.endText();
            yTexto -= altoLinea;
        }
    }

    private void dibujarFotoCircular(PdfContentByte lienzo, ApoyoCredencialDTO c,
                                     Disposicion d, float w, float h, float x0, float y0) {
        if (c.fotoUrl() == null || c.fotoUrl().isBlank()) return;
        Path ruta = rutaFoto(c.fotoUrl());
        if (ruta == null) return;
        try {
            Image foto = Image.getInstance(ruta.toAbsolutePath().toString());
            float cx = x0 + (float) (d.avatarCx() * w);
            float cy = y0 + h - (float) (d.avatarCy() * h);
            float radio = (float) (d.avatarRadio() * w);

            float s = Math.max(2 * radio / foto.getWidth(), 2 * radio / foto.getHeight());
            float fw = foto.getWidth() * s;
            float fh = foto.getHeight() * s;
            foto.scaleAbsolute(fw, fh);
            foto.setAbsolutePosition(cx - fw / 2f, cy - fh / 2f);

            lienzo.saveState();
            lienzo.circle(cx, cy, radio);
            lienzo.clip();
            lienzo.newPath();
            lienzo.addImage(foto);
            lienzo.restoreState();
        } catch (Exception e) {
            log.warn("Foto ilegible en la credencial de apoyo {} ({}): {}. Sale sin ella.",
                    c.nombreCompleto(), c.fotoUrl(), e.getMessage());
        }
    }

    private void marcasDeCorte(PdfContentByte lienzo, float x, float y, float w, float h) {
        float m = 8f;
        lienzo.setLineWidth(0.5f);
        lienzo.setColorStroke(BaseColor.LIGHT_GRAY);
        lienzo.moveTo(x, y + m); lienzo.lineTo(x, y);
        lienzo.moveTo(x + m, y); lienzo.lineTo(x, y);
        lienzo.moveTo(x + w - m, y); lienzo.lineTo(x + w, y);
        lienzo.moveTo(x + w, y + m); lienzo.lineTo(x + w, y);
        lienzo.moveTo(x + w, y + h - m); lienzo.lineTo(x + w, y + h);
        lienzo.moveTo(x + w - m, y + h); lienzo.lineTo(x + w, y + h);
        lienzo.moveTo(x + m, y + h); lienzo.lineTo(x, y + h);
        lienzo.moveTo(x, y + h - m); lienzo.lineTo(x, y + h);
        lienzo.stroke();
    }

    private void lineaDeCorte(PdfContentByte lienzo, float coord, float ini, float fin, boolean horizontal) {
        lienzo.setLineWidth(0.3f);
        lienzo.setColorStroke(BaseColor.LIGHT_GRAY);
        lienzo.setLineDash(4f, 4f);
        if (horizontal) {
            lienzo.moveTo(ini, coord);
            lienzo.lineTo(fin, coord);
        } else {
            lienzo.moveTo(coord, ini);
            lienzo.lineTo(coord, fin);
        }
        lienzo.stroke();
        lienzo.setLineDash(0f);
    }
}

package com.usic.uniFex.model.service;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;

import com.usic.uniFex.model.dto.CredencialDTO;
import com.usic.uniFex.model.dto.PlantillaCredencial;
import com.usic.uniFex.model.dto.PlantillaCredencial.Caja;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CredencialPdfService {

    private final ImagenPlantillaCredencial imagenes;

    public static final double ANCHO_CM_POR_DEFECTO = 10.0;
    private static final float CM = 72f / 2.54f;
    public static final float A4_ANCHO_CM = 21f;
    public static final float A4_ALTO_CM = 29.7f;
    public static final float CRED_ANCHO_CM = 10f;
    public static final float CRED_ALTO_CM = 13f;

    @Value("${app.upload-root:uploads}")
    private String uploadRoot;

    public double proporcion(PlantillaCredencial plantilla) {
        try {
            Image img = Image.getInstance(bytes(plantilla.imagen()));
            return img.getHeight() / img.getWidth();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo leer la plantilla " + plantilla.id() + ": " + e.getMessage(), e);
        }
    }

    public byte[] generar(List<CredencialDTO> credenciales, PlantillaCredencial plantilla,
                          double anchoCm, String urlBase, CredencialCodigoService codigos) {
        if (credenciales == null || credenciales.isEmpty()) {
            throw new IllegalArgumentException("No hay credenciales que imprimir");
        }
        double ancho = Math.max(4.0, Math.min(20.0, anchoCm));

        Document doc = new Document(PageSize.LETTER, 0, 0, 0, 0);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            doc.open();
            PdfContentByte lienzo = writer.getDirectContent();
            BaseFont fuente = BaseFont.createFont(
                    BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            Image fondoOriginal = Image.getInstance(bytes(plantilla.imagen()));

            for (int i = 0; i < credenciales.size(); i++) {
                if (i > 0) doc.newPage();
                pintar(lienzo, fuente, fondoOriginal, plantilla, credenciales.get(i),
                        ancho, urlBase, codigos);
            }
            doc.close();
            log.info("Credenciales impresas: {} (plantilla {}, {} cm de ancho)",
                    credenciales.size(), plantilla.id(), ancho);
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF de credenciales: "
                    + e.getMessage(), e);
        }
    }

    private void pintar(PdfContentByte lienzo, BaseFont fuente, Image plantillaImg,
                        PlantillaCredencial d, CredencialDTO c, double anchoCm, String urlBase,
                        CredencialCodigoService codigos) throws Exception {

        Rectangle hoja = PageSize.LETTER;
        float w = (float) (anchoCm * CM);
        float h = w * plantillaImg.getHeight() / plantillaImg.getWidth();
        float x0 = (hoja.getWidth() - w) / 2f;
        float y0 = (hoja.getHeight() - h) / 2f;

        pintarCredencial(lienzo, fuente, plantillaImg, d, c, x0, y0, w, h, urlBase, codigos);
    }

    private void pintarCredencial(PdfContentByte lienzo, BaseFont fuente, Image plantillaImg,
                                  PlantillaCredencial d, CredencialDTO c,
                                  float x0, float y0, float w, float h, String urlBase,
                                  CredencialCodigoService codigos) throws Exception {

        Image fondo = Image.getInstance(plantillaImg);
        fondo.scaleAbsolute(w, h);
        fondo.setAbsolutePosition(x0, y0);
        lienzo.addImage(fondo);

        pintarContenido(lienzo, fuente, d, c, x0, y0, w, h, urlBase, codigos);
    }

    private void pintarContenido(PdfContentByte lienzo, BaseFont fuente,
                                 PlantillaCredencial d, CredencialDTO c,
                                 float x0, float y0, float w, float h, String urlBase,
                                 CredencialCodigoService codigos) throws Exception {

        float lado = (float) (d.qr().ancho() * w);
        float qrX = x0 + (float) (d.qr().x() * w);
        float qrY = y0 + h - (float) (d.qr().y() * h) - lado;
        lienzo.saveState();
        lienzo.setColorFill(BaseColor.WHITE);
        lienzo.rectangle(qrX, qrY, lado, lado);
        lienzo.fill();
        lienzo.restoreState();
        BarcodeQRCode qr = new BarcodeQRCode(
                codigos.urlPublica(urlBase, c.codigo()), 400, 400, null);
        Image imgQr = qr.getImage();
        imgQr.scaleAbsolute(lado, lado);
        imgQr.setAbsolutePosition(qrX, qrY);
        lienzo.addImage(imgQr);

        String empresa = c.entidad() == null ? "" : c.entidad();
        if (c.rubro() != null && !c.rubro().isBlank()) empresa += "  ·  " + c.rubro();

        textoDato(lienzo, fuente, d.nombre(), valor(c.nombre(), d), x0, y0, w, h, d);
        textoDato(lienzo, fuente, d.empresa(), valor(empresa, d), x0, y0, w, h, d);
        textoDato(lienzo, fuente, d.ci(), valor(c.ci(), d), x0, y0, w, h, d);
        textoDato(lienzo, fuente, d.tipo(), valor(tipoDe(c, d), d), x0, y0, w, h, d);
        dibujarFoto(lienzo, d.foto(), c, x0, y0, w, h, d.fotoCircular());
        if (d.zona() != null) {
            textoDato(lienzo, fuente, d.codigo(), valor(c.casetas(), d), x0, y0, w, h, d);
            textoDato(lienzo, fuente, d.zona(), valor(c.categoria(), d), x0, y0, w, h, d);
        } else {
            codigoConCategoria(lienzo, fuente, d.codigo(), valor(c.casetas(), d),
                    valor(c.categoria(), d), x0, y0, w, h, d.centrado(), tinta(d));
        }
    }

    private void codigoConCategoria(PdfContentByte lienzo, BaseFont fuente, Caja caja,
                                    String codigo, String categoria, float x0, float y0,
                                    float w, float h, boolean centrado, BaseColor color) {
        if (caja == null || codigo == null || codigo.isBlank()) return;

        float cajaW = (float) (caja.ancho() * w);
        float cajaH = (float) (caja.alto() * h);
        float x = x0 + (float) (caja.x() * w);
        float y = y0 + h - (float) (caja.y() * h) - cajaH;
        boolean hayCategoria = categoria != null && !categoria.isBlank();

        float altoNumero = hayCategoria ? cajaH * 0.62f : cajaH;
        float tamNumero = ajustar(fuente, codigo, cajaW, altoNumero * 0.92f, altoNumero * 0.35f);
        int alineacion = centrado ? Element.ALIGN_CENTER : Element.ALIGN_LEFT;
        float xTexto = centrado ? x + cajaW / 2f : x;

        lienzo.beginText();
        lienzo.setFontAndSize(fuente, tamNumero);
        lienzo.setColorFill(color);
        lienzo.showTextAligned(alineacion, codigo, xTexto,
                y + cajaH - altoNumero + (altoNumero - tamNumero * 0.70f) / 2f, 0);
        lienzo.endText();

        if (!hayCategoria) return;
        float altoCat = cajaH - altoNumero;
        float tamCat = ajustar(fuente, categoria, cajaW, altoCat * 0.86f, altoCat * 0.30f);
        lienzo.beginText();
        lienzo.setFontAndSize(fuente, tamCat);
        lienzo.setColorFill(new BaseColor(70, 70, 70));
        lienzo.showTextAligned(alineacion, categoria, xTexto,
                y + (altoCat - tamCat * 0.70f) / 2f, 0);
        lienzo.endText();
    }

    private static float ajustar(BaseFont fuente, String texto, float ancho,
                                 float maximo, float minimo) {
        float t = maximo;
        while (t > minimo && fuente.getWidthPoint(texto, t) > ancho) t -= 0.5f;
        return t;
    }

    private static String valor(String s, PlantillaCredencial d) {
        String t = s == null ? "" : s.trim();
        return d.mayusculas() ? t.toUpperCase() : t;
    }

    private void texto(PdfContentByte lienzo, BaseFont fuente, Caja caja, String contenido,
                       float x0, float y0, float w, float h, boolean centrado, BaseColor color) {
        if (caja == null || contenido == null || contenido.isBlank()) return;

        float cajaW = (float) (caja.ancho() * w);
        float cajaH = (float) (caja.alto() * h);
        float x = x0 + (float) (caja.x() * w);
        float y = y0 + h - (float) (caja.y() * h) - cajaH;

        float tam = cajaH * 0.72f;
        float minimo = cajaH * 0.32f;
        while (tam > minimo && fuente.getWidthPoint(contenido, tam) > cajaW) {
            tam -= 0.5f;
        }
        String pintable = contenido;
        if (fuente.getWidthPoint(pintable, tam) > cajaW) {
            while (pintable.length() > 4 && fuente.getWidthPoint(pintable + "…", tam) > cajaW) {
                pintable = pintable.substring(0, pintable.length() - 1);
            }
            pintable = pintable + "…";
        }

        lienzo.beginText();
        lienzo.setFontAndSize(fuente, tam);
        lienzo.setColorFill(color);
        lienzo.showTextAligned(
                centrado ? Element.ALIGN_CENTER : Element.ALIGN_LEFT,
                pintable,
                centrado ? x + cajaW / 2f : x,
                y + (cajaH - tam * 0.70f) / 2f, 0);
        lienzo.endText();
    }

    private void textoDato(PdfContentByte lienzo, BaseFont fuente, Caja caja, String contenido,
                           float x0, float y0, float w, float h, PlantillaCredencial plantilla) {
        if (caja == null || contenido == null || contenido.isBlank()) return;
        if (!plantilla.multilinea()) {
            texto(lienzo, fuente, caja, contenido, x0, y0, w, h, plantilla.centrado(), tinta(plantilla));
            return;
        }
        float ancho = (float) caja.ancho() * w;
        float alto = (float) caja.alto() * h;
        String limpio = contenido.replaceAll("\\s+", " ").trim();
        for (float tam = Math.min(12f, alto * .72f); tam >= 6f; tam -= .25f) {
            var lineas = new java.util.ArrayList<String>();
            String linea = "";
            boolean palabraLarga = false;
            for (String palabra : limpio.split(" ")) {
                if (fuente.getWidthPoint(palabra, tam) > ancho) { palabraLarga = true; break; }
                String candidata = linea.isEmpty() ? palabra : linea + " " + palabra;
                if (!linea.isEmpty() && fuente.getWidthPoint(candidata, tam) > ancho) {
                    lineas.add(linea);
                    linea = palabra;
                } else linea = candidata;
            }
            if (palabraLarga) continue;
            if (!linea.isEmpty()) lineas.add(linea);
            float ascenso = fuente.getFontDescriptor(BaseFont.ASCENT, tam);
            float descenso = fuente.getFontDescriptor(BaseFont.DESCENT, tam);
            float interlineado = tam * 1.1f;
            float altoTexto = ascenso - descenso + (lineas.size() - 1) * interlineado;
            if (lineas.size() > 2 || altoTexto > alto) continue;
            float x = x0 + (float) caja.x() * w;
            float y = y0 + h - (float) caja.y() * h - alto;
            float base = y + (alto + altoTexto) / 2 - ascenso;
            lienzo.beginText();
            lienzo.setFontAndSize(fuente, tam);
            lienzo.setColorFill(tinta(plantilla));
            for (String parte : lineas) {
                lienzo.showTextAligned(Element.ALIGN_LEFT, parte, x, base, 0);
                base -= interlineado;
            }
            lienzo.endText();
            return;
        }
        texto(lienzo, fuente, caja, limpio, x0, y0, w, h, false, tinta(plantilla));
    }

    static String tipoDe(CredencialDTO c, PlantillaCredencial d) {
        return c.cargo() != null && !c.cargo().isBlank() ? c.cargo() : d.tipoTexto();
    }

    static BaseColor tinta(PlantillaCredencial d) {
        java.awt.Color c = colorDe(d.colorTexto(), java.awt.Color.BLACK);
        return new BaseColor(c.getRed(), c.getGreen(), c.getBlue());
    }

    static java.awt.Color colorDe(String hex, java.awt.Color porDefecto) {
        if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) return porDefecto;
        return new java.awt.Color(Integer.parseInt(hex.substring(1), 16));
    }

    private void dibujarFoto(PdfContentByte lienzo, Caja caja, CredencialDTO c,
                             float x0, float y0, float w, float h, boolean circular) throws Exception {
        if (caja == null || c.fotoUrl() == null || c.fotoUrl().isBlank()) return;
        Path ruta = rutaFoto(c.fotoUrl());
        if (ruta == null) return;

        float cajaW = (float) (caja.ancho() * w);
        float cajaH = (float) (caja.alto() * h);
        float x = x0 + (float) (caja.x() * w);
        float y = y0 + h - (float) (caja.y() * h) - cajaH;

        try {
            Image foto = fotoReducida(ruta);
            if (circular) {
                float escala = Math.max(cajaW / foto.getWidth(), cajaH / foto.getHeight());
                foto.scaleAbsolute(foto.getWidth() * escala, foto.getHeight() * escala);
                foto.setAbsolutePosition(x + (cajaW - foto.getScaledWidth()) / 2,
                        y + (cajaH - foto.getScaledHeight()) / 2);
                lienzo.saveState();
                try {
                    lienzo.ellipse(x, y, x + cajaW, y + cajaH);
                    lienzo.clip();
                    lienzo.newPath();
                    lienzo.addImage(foto);
                } finally {
                    lienzo.restoreState();
                }
                return;
            }
            foto.scaleAbsolute(cajaW, cajaH);
            foto.setAbsolutePosition(x, y);
            lienzo.addImage(foto);
        } catch (Exception e) {
            log.warn("Foto ilegible en la credencial de {} ({}): {}. Se imprime sin ella.",
                    c.nombre(), c.fotoUrl(), e.getMessage());
        }
    }

    private static final int FOTO_MAX_PX = 360;

    private Image fotoReducida(Path ruta) throws Exception {
        BufferedImage original = ImageIO.read(ruta.toFile());
        if (original == null) throw new java.io.IOException("Formato de imagen no reconocido");
        int ancho = original.getWidth(), alto = original.getHeight();
        double escala = Math.min(1.0, FOTO_MAX_PX / (double) Math.max(ancho, alto));
        BufferedImage lista = original;
        if (escala < 1.0 || original.getColorModel().hasAlpha()) {
            int nuevoAncho = Math.max(1, (int) (ancho * escala));
            int nuevoAlto = Math.max(1, (int) (alto * escala));
            BufferedImage chica = new BufferedImage(nuevoAncho, nuevoAlto, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = chica.createGraphics();
            try {
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, nuevoAncho, nuevoAlto);
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(original, 0, 0, nuevoAncho, nuevoAlto, null);
            } finally {
                g.dispose();
            }
            lista = chica;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(lista, "jpg", bos);
        return Image.getInstance(bos.toByteArray());
    }

    private Path rutaFoto(String fotoUrl) {
        String relativa = fotoUrl.startsWith("/files/") ? fotoUrl.substring("/files/".length()) : fotoUrl;
        try {
            Path base = Paths.get(uploadRoot).toAbsolutePath().normalize();
            Path destino = base.resolve(relativa).normalize();
            if (!destino.startsWith(base) || !Files.isRegularFile(destino)) return null;
            return destino;
        } catch (RuntimeException e) {
            log.warn("No se pudo resolver la foto {}: {}", fotoUrl, e.getMessage());
            return null;
        }
    }

    public static final int GRUPO_DUPLEX = 4;

    public byte[] generarGrupoDe4(List<CredencialDTO> grupo, PlantillaCredencial plantilla,
                                  double anchoCm, double altoCm,
                                  String urlBase, CredencialCodigoService codigos) {
        if (grupo == null || grupo.isEmpty() || grupo.size() > GRUPO_DUPLEX) {
            throw new IllegalArgumentException("El lote debe tener entre 1 y 4 credenciales");
        }
        float[] medidas = medidasImpresas(plantilla, anchoCm, altoCm);
        Rectangle hoja = new Rectangle(A4_ANCHO_CM * CM, A4_ALTO_CM * CM);
        Document doc = new Document(hoja, 0, 0, 0, 0);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            Image frente = Image.getInstance(bytes(plantilla.imagen()));
            float w = medidas[0], h = medidas[1];
            validarCuadricula(hoja, w, h);
            float x0 = (hoja.getWidth() - 2 * w) / 2;
            float y0 = (hoja.getHeight() - 2 * h) / 2;
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            preferenciaTamanoReal(writer);
            doc.open();
            PdfContentByte lienzo = writer.getDirectContent();
            BaseFont fuente = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            com.itextpdf.text.pdf.PdfTemplate tplFrente = plantillaFrente(lienzo, frente, w, h);
            com.itextpdf.text.pdf.PdfTemplate tplReverso = plantillaReverso(lienzo, w, h, plantilla);

            pintarGrupo(lienzo, fuente, tplFrente, tplReverso, grupo, plantilla,
                    x0, y0, w, h, urlBase, codigos, false);
            doc.newPage();
            pintarGrupo(lienzo, fuente, tplFrente, tplReverso, grupo, plantilla,
                    x0, y0, w, h, urlBase, codigos, true);
            doc.close();
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF del lote: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
    }

    public byte[] generarDuplex4(List<CredencialDTO> credenciales, PlantillaCredencial plantilla,
                                 double anchoCm, double altoCm,
                                 String urlBase, CredencialCodigoService codigos) {
        if (credenciales == null || credenciales.isEmpty()) {
            throw new IllegalArgumentException("No hay credenciales que imprimir");
        }
        float[] medidas = medidasImpresas(plantilla, anchoCm, altoCm);
        Rectangle hoja = new Rectangle(A4_ANCHO_CM * CM, A4_ALTO_CM * CM);
        Document doc = new Document(hoja, 0, 0, 0, 0);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            Image frente = Image.getInstance(bytes(plantilla.imagen()));
            float w = medidas[0], h = medidas[1];
            validarCuadricula(hoja, w, h);
            float x0 = (hoja.getWidth() - 2 * w) / 2;
            float y0 = (hoja.getHeight() - 2 * h) / 2;
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            preferenciaTamanoReal(writer);
            doc.open();
            PdfContentByte lienzo = writer.getDirectContent();
            BaseFont fuente = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            com.itextpdf.text.pdf.PdfTemplate tplFrente = plantillaFrente(lienzo, frente, w, h);
            com.itextpdf.text.pdf.PdfTemplate tplReverso = plantillaReverso(lienzo, w, h, plantilla);

            int total = (credenciales.size() + GRUPO_DUPLEX - 1) / GRUPO_DUPLEX;
            for (int lote = 0; lote < total; lote++) {
                List<CredencialDTO> grupo = credenciales.subList(
                        lote * GRUPO_DUPLEX, Math.min(credenciales.size(), (lote + 1) * GRUPO_DUPLEX));
                if (lote > 0) doc.newPage();
                pintarGrupo(lienzo, fuente, tplFrente, tplReverso, grupo, plantilla,
                        x0, y0, w, h, urlBase, codigos, false);
                doc.newPage();
                pintarGrupo(lienzo, fuente, tplFrente, tplReverso, grupo, plantilla,
                        x0, y0, w, h, urlBase, codigos, true);
            }
            doc.close();
            log.info("Credenciales duplex 2x2: {}, hojas: {}", credenciales.size(), 2 * total);
            return salida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF duplex: " + e.getMessage(), e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
    }

    private float[] medidasImpresas(PlantillaCredencial plantilla, double anchoCm, double altoCm) {
        if (!Double.isFinite(anchoCm) || anchoCm <= 0 || !Double.isFinite(altoCm) || altoCm <= 0) {
            throw new IllegalArgumentException("Las medidas de la credencial deben ser positivas");
        }
        if (plantilla == null || plantilla.qr() == null) {
            throw new IllegalArgumentException("La plantilla no trae disposicion del QR");
        }
        return new float[]{(float) (anchoCm * CM), (float) (altoCm * CM)};
    }

    private void validarCuadricula(Rectangle hoja, float w, float h) {
        if (2 * w > hoja.getWidth() || 2 * h > hoja.getHeight()) {
            throw new IllegalArgumentException("Cuatro credenciales de ese tamano no caben en la hoja");
        }
    }

    private void preferenciaTamanoReal(PdfWriter writer) {
        writer.addViewerPreference(com.itextpdf.text.pdf.PdfName.PRINTSCALING,
                com.itextpdf.text.pdf.PdfName.NONE);
        writer.addViewerPreference(com.itextpdf.text.pdf.PdfName.DUPLEX,
                com.itextpdf.text.pdf.PdfName.DUPLEXFLIPLONGEDGE);
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

    private void pintarGrupo(PdfContentByte lienzo, BaseFont fuente,
                             com.itextpdf.text.pdf.PdfTemplate tplFrente,
                             com.itextpdf.text.pdf.PdfTemplate tplReverso,
                             List<CredencialDTO> grupo, PlantillaCredencial plantilla,
                             float x0, float y0, float w, float h,
                             String urlBase, CredencialCodigoService codigos,
                             boolean reversos) throws Exception {
        boolean[] columna = new boolean[2];
        boolean[] fila = new boolean[2];
        for (int i = 0; i < grupo.size(); i++) {
            int col = reversos ? 1 - (i % 2) : i % 2;
            boolean arriba = i < 2;
            float x = x0 + col * w, y = arriba ? y0 + h : y0;
            if (reversos) {
                lienzo.addTemplate(tplReverso, x, y);
            } else {
                lienzo.addTemplate(tplFrente, x, y);
                pintarContenido(lienzo, fuente, plantilla, grupo.get(i),
                        x, y, w, h, urlBase, codigos);
            }
            marcasDeCorte(lienzo, x, y, w, h);
            columna[col] = true;
            fila[arriba ? 1 : 0] = true;
        }
        if (columna[0] && columna[1]) lineaDeCorte(lienzo, x0 + w, y0, y0 + 2 * h);
        if (fila[0] && fila[1]) lineaDeCorte(lienzo, y0 + h, x0, x0 + 2 * w, true);
    }

    public static final String REVERSO_POR_DEFECTO = "static/assets/CREDENCIAL_REVERSO.png";

    public byte[] imagen(String recurso) {
        return bytes(recurso);
    }

    public void pintarFrente(PdfContentByte lienzo, PlantillaCredencial p, CredencialDTO c,
                             float x, float y, float w, float h, String urlBase,
                             CredencialCodigoService codigos) throws Exception {
        pintarCredencial(lienzo, fuente(), Image.getInstance(bytes(p.imagen())), p, c, x, y, w, h, urlBase, codigos);
    }

    public void pintarSoloContenido(PdfContentByte lienzo, PlantillaCredencial p, CredencialDTO c,
                                    float x, float y, float w, float h, String urlBase,
                                    CredencialCodigoService codigos) throws Exception {
        pintarContenido(lienzo, fuente(), p, c, x, y, w, h, urlBase, codigos);
    }

    private static BaseFont fuente() throws Exception {
        return BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
    }

    private com.itextpdf.text.pdf.PdfTemplate plantillaReverso(
            PdfContentByte lienzo, float w, float h, PlantillaCredencial plantilla) throws Exception {
        String reverso = plantilla.imagenReverso() != null ? plantilla.imagenReverso() : REVERSO_POR_DEFECTO;
        Image base = Image.getInstance(bytes(reverso));
        float escala = Math.max(w / base.getWidth(), h / base.getHeight());
        float ancho = base.getWidth() * escala;
        float alto = base.getHeight() * escala;
        Image fondo = Image.getInstance(base);
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

    private void lineaDeCorte(PdfContentByte lienzo, float x, float y0, float y1) {
        lineaDeCorte(lienzo, x, y0, y1, false);
    }

    private void lineaDeCorte(PdfContentByte lienzo, float pos, float desde, float hasta,
                              boolean horizontal) {
        lienzo.saveState();
        lienzo.setColorStroke(new BaseColor(150, 150, 150));
        lienzo.setLineWidth(.5f);
        if (horizontal) {
            lienzo.moveTo(desde, pos);
            lienzo.lineTo(hasta, pos);
        } else {
            lienzo.moveTo(pos, desde);
            lienzo.lineTo(pos, hasta);
        }
        lienzo.stroke();
        lienzo.restoreState();
    }

    private void marcasDeCorte(PdfContentByte lienzo, float x, float y, float w, float h) {
        float separacion = .06f * CM;
        float largo = .18f * CM;
        lienzo.saveState();
        lienzo.setColorStroke(new BaseColor(150, 150, 150));
        lienzo.setLineWidth(.3f);
        for (float bordeX : new float[]{x, x + w}) {
            float direccion = bordeX == x ? -1 : 1;
            for (float bordeY : new float[]{y, y + h}) {
                lienzo.moveTo(bordeX + direccion * separacion, bordeY);
                lienzo.lineTo(bordeX + direccion * (separacion + largo), bordeY);
                float vertical = bordeY == y ? -1 : 1;
                lienzo.moveTo(bordeX, bordeY + vertical * separacion);
                lienzo.lineTo(bordeX, bordeY + vertical * (separacion + largo));
            }
        }
        lienzo.stroke();
        lienzo.restoreState();
    }

    private byte[] bytes(String recurso) {
        return imagenes.bytes(recurso);
    }
}

package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.itextpdf.text.pdf.PdfReader;
import com.usic.uniFex.model.service.EntradaDisenoService;
import com.usic.uniFex.model.service.EntradaService;
import com.usic.uniFex.model.service.EntradaService.Compra;
import com.usic.uniFex.model.service.EntradaService.Item;

@SpringBootTest
@ActiveProfiles("dev")
class EntradaDigitalTest {

    @Autowired private EntradaService entradas;
    @Autowired private EntradaDisenoService disenos;
    @Autowired private JdbcTemplate jdbc;

    private Long edicion;
    private Long general;
    private Long usuario;

    @BeforeEach
    void preparar() {
        edicion = jdbc.queryForObject("""
                INSERT INTO edicion (nombre, anio, activa, plano_version, plano_sin_fondo, publica, publicacion_independiente,
                                     recibe_interesados, vende_entradas, vende_espacios, pago_oficina, fechas_texto, lugar)
                VALUES (?, 2026, FALSE, 0, TRUE, TRUE, TRUE, TRUE, TRUE, FALSE, 'Oficina', 'Sábado 20:00', 'Coliseo') RETURNING id
                """, Long.class, "Prueba entrada digital " + UUID.randomUUID());
        general = jdbc.queryForObject("INSERT INTO entrada_tipo (id_edicion, nombre, precio, cupo, orden) VALUES (?, 'General', 30, NULL, 1) RETURNING id",
                Long.class, edicion);
        usuario = jdbc.queryForObject("SELECT min(id) FROM usuario", Long.class);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM entrada_compra WHERE id_edicion = ?", edicion);
        jdbc.update("DELETE FROM entrada_tipo WHERE id_edicion = ?", edicion);
        jdbc.update("DELETE FROM edicion WHERE id = ?", edicion);
    }

    private String compraAprobada(int cantidad, String extra) throws Exception {
        var r = entradas.comprarEnLinea(edicion, new Compra("Ana Prueba Rojas", "1234567", "71112222", "OFICINA", true,
                List.of(new Item(general, cantidad)), extra), null, "prueba");
        assertThat(r.ok()).as(r.mensaje()).isTrue();
        Long id = ((Number) r.datos().get("id")).longValue();
        assertThat(entradas.aprobar(id, usuario).ok()).isTrue();
        return (String) r.datos().get("codigo");
    }

    private static BufferedImage imagen(byte[] png) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    @Test
    void cadaDisenoGeneraUnaEntradaVerticalParaCelular() throws Exception {
        assertThat(disenos.disenoDe(edicion).estilo()).isEqualTo("NOCHE");
        for (String estilo : List.of("NOCHE", "FIESTA", "ELEGANTE", "FRESCO", "SOLO_QR")) {
            BufferedImage img = imagen(disenos.vistaPrevia(edicion, estilo, true));
            assertThat(img.getWidth()).as(estilo).isEqualTo(EntradaDisenoService.ANCHO);
            assertThat(img.getHeight()).as(estilo).isEqualTo(EntradaDisenoService.ALTO);
        }
        assertThat(disenos.guardar(edicion, "FONDO", true)).contains("sube la imagen");
        assertThat(disenos.guardar(edicion, "FIESTA", false)).isNull();
        assertThat(disenos.disenoDe(edicion)).satisfies(d -> {
            assertThat(d.estilo()).isEqualTo("FIESTA");
            assertThat(d.datos()).isFalse();
        });
    }

    @Test
    void elPdfTraeUnaPaginaPorEntradaYCadaPaseSuImagen() throws Exception {
        String codigo = compraAprobada(3, null);
        PdfReader pdf = new PdfReader(entradas.pdfPases(codigo));
        assertThat(pdf.getNumberOfPages()).isEqualTo(3);
        pdf.close();
        assertThat(imagen(entradas.pngPase(codigo, 2)).getHeight()).isEqualTo(EntradaDisenoService.ALTO);
        assertThat(entradas.pngPase(codigo, 9)).isNull();
        assertThat(entradas.pngPases(codigo)).hasSize(3);
    }

    @Test
    void unQrDentroNoPuedeVolverAEntrar() throws Exception {
        String compra = compraAprobada(1, null);
        String pase = (String) ((List<?>) entradas.consultar(compra).get("pases")).stream().map(x -> (Map<?, ?>) x).toList().getFirst().get("codigo");
        assertThat(entradas.registrarIngreso(pase, "S", usuario)).containsEntry("ok", false);
        assertThat(entradas.registrarIngreso(pase, "E", usuario)).containsEntry("ok", true).containsEntry("sentido", "E");
        jdbc.update("UPDATE entrada_pase_acceso SET cuando = cuando - interval '1 minute' WHERE id_pase IN (SELECT id FROM entrada_pase WHERE codigo = ?)", pase);
        Map<String, Object> otra = entradas.registrarIngreso(pase, "E", usuario);
        assertThat(otra).containsEntry("ok", false).containsEntry("yaDentro", true);
        assertThat((String) otra.get("mensaje")).contains("ya se usó para entrar");
        assertThat(entradas.registrarIngreso(pase, "S", usuario)).containsEntry("ok", true).containsEntry("sentido", "S");
    }

    @Test
    void guardaElOtroWhatsAppSoloSiEsValidoYDistinto() throws Exception {
        compraAprobada(1, "7333 4444");
        compraAprobada(1, "71112222");
        compraAprobada(1, "abc");
        List<String> extras = jdbc.queryForList("SELECT celular_extra FROM entrada_compra WHERE id_edicion = ? ORDER BY id", String.class, edicion);
        assertThat(extras).containsExactly("73334444", null, null);
        assertThat(entradas.listar(edicion, null)).anyMatch(c -> "73334444".equals(c.get("celularExtra")));
    }
}

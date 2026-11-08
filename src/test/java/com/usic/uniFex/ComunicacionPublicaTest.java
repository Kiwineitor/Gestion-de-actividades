package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.usic.uniFex.model.dto.AnuncioDTO;
import com.usic.uniFex.model.service.AnuncioService;
import com.usic.uniFex.model.service.AnuncioService.NuevoAnuncio;

@SpringBootTest
@ActiveProfiles("dev")
class ComunicacionPublicaTest {

    @Autowired AnuncioService anuncios;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> creados = new ArrayList<>();
    private Long visible;
    private Long oculta;

    @BeforeEach
    void preparar() {
        visible = jdbc.queryForObject("""
                INSERT INTO edicion (nombre, anio, activa, publica, publicacion_independiente, contacto)
                VALUES ('Prueba promos visible', 2026, FALSE, TRUE, TRUE, '71112233') RETURNING id
                """, Long.class);
        oculta = jdbc.queryForObject("""
                INSERT INTO edicion (nombre, anio, activa, publica, publicacion_independiente)
                VALUES ('Prueba promos oculta', 2026, FALSE, FALSE, FALSE) RETURNING id
                """, Long.class);
    }

    @AfterEach
    void limpiar() {
        for (Long id : creados) jdbc.update("DELETE FROM anuncio WHERE id = ?", id);
        jdbc.update("DELETE FROM anuncio WHERE id_edicion IN (?, ?)", visible, oculta);
        jdbc.update("DELETE FROM edicion WHERE id IN (?, ?)", visible, oculta);
    }

    private static NuevoAnuncio combo(String titulo, Long edicion, String precio, String antes) {
        return new NuevoAnuncio(titulo, "Para toda la familia", null, null, "INTERNO", null, null, null,
                "COMBO", null, edicion, precio == null ? null : new BigDecimal(precio), antes == null ? null : new BigDecimal(antes),
                "Mesa para 6\n\n 2 jarras ", "Lo quiero", "WHATSAPP");
    }

    private AnuncioDTO publicar(NuevoAnuncio n) {
        AnuncioDTO d = anuncios.publicar(n, null);
        creados.add(d.id());
        return d;
    }

    private List<AnuncioDTO> publicos() {
        return anuncios.publicos().stream().filter(a -> creados.contains(a.id())).toList();
    }

    @Test
    void validaLoQueCadaFormatoNecesita() {
        assertThatThrownBy(() -> publicar(combo("Sin precio", visible, null, null))).hasMessageContaining("precio del combo");
        assertThatThrownBy(() -> publicar(combo("Rebaja al revés", visible, "100", "80"))).hasMessageContaining("precio anterior");
        assertThatThrownBy(() -> publicar(new NuevoAnuncio(null, "x", null, null, null, null, null, null,
                "DESTACADO", null, null, null, null, null, null, null))).hasMessageContaining("título");
        assertThatThrownBy(() -> publicar(new NuevoAnuncio("Imagen ajena", null, null, null, null, null, null, null,
                "DESTACADO", "../../application-dev.properties", null, null, null, null, null, null))).hasMessageContaining("imagen");
        assertThatThrownBy(() -> publicar(new NuevoAnuncio("Sin actividad", "x", null, null, null, null, null, null,
                "DESTACADO", null, null, null, null, null, "Ver", "ACTIVIDAD"))).hasMessageContaining("elige una actividad");
        assertThatThrownBy(() -> publicar(new NuevoAnuncio("Sin enlace", "x", null, null, null, null, null, null,
                "EMERGENTE", null, null, null, null, null, "Ver", "ENLACE"))).hasMessageContaining("enlace");
    }

    @Test
    void elComboSalePublicoConSuActividadYContacto() {
        AnuncioDTO d = publicar(combo("Combo familiar", visible, "150", "200"));
        assertThat(d.destino()).isEqualTo("PUBLICO");
        assertThat(d.incluye()).containsExactly("Mesa para 6", "2 jarras");

        AnuncioDTO p = publicos().get(0);
        assertThat(p.formato()).isEqualTo("COMBO");
        assertThat(p.edicionNombre()).isEqualTo("Prueba promos visible");
        assertThat(p.contacto()).isEqualTo("71112233");
        assertThat(p.precioAntes()).isEqualByComparingTo("200");
    }

    @Test
    void noSeMuestraLoDeUnaActividadOcultaNiLoProgramado() {
        publicar(combo("De actividad oculta", oculta, "50", null));
        publicar(new NuevoAnuncio("Lanzamiento", null, null, null, null, LocalDateTime.now().plusDays(2), null, null,
                "EMERGENTE", null, null, null, null, null, null, null));
        AnuncioDTO general = publicar(new NuevoAnuncio("General", "Para todos", null, 60, null, null, null, null,
                "DESTACADO", null, null, null, null, null, null, null));
        assertThat(publicos()).extracting(AnuncioDTO::id).containsExactly(general.id());
    }

    @Test
    void cuentaVistasYClicsSoloDeLoPublico() {
        AnuncioDTO d = publicar(combo("Contado", visible, "10", null));
        AnuncioDTO interno = publicar(new NuevoAnuncio(null, "Solo equipo", "info", 30, "INTERNO", null, null, null,
                "AVISO", null, null, null, null, null, null, null));
        assertThat(anuncios.contar(d.id(), false)).isTrue();
        assertThat(anuncios.contar(d.id(), false)).isTrue();
        assertThat(anuncios.contar(d.id(), true)).isTrue();
        assertThat(anuncios.contar(interno.id(), false)).isFalse();
        AnuncioDTO p = publicos().stream().filter(a -> a.id().equals(d.id())).findFirst().orElseThrow();
        assertThat(p.vistas()).isEqualTo(2);
        assertThat(p.clics()).isEqualTo(1);
    }

    @Test
    void editarYRetirarSeReflejanEnLaPaginaPublica() {
        AnuncioDTO d = publicar(combo("Primero", visible, "10", null));
        anuncios.editar(d.id(), combo("Cambiado", visible, "12", "15"), null);
        assertThat(publicos().get(0).titulo()).isEqualTo("Cambiado");
        assertThat(anuncios.retirar(d.id(), null)).isTrue();
        assertThat(publicos()).isEmpty();
        assertThatThrownBy(() -> anuncios.editar(d.id(), combo("Otra vez", visible, "12", null), null))
                .hasMessageContaining("retirada");
    }
}

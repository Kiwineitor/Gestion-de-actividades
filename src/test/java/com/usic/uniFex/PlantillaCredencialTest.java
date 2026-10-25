package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.usic.uniFex.model.dto.PlantillaCredencial;

class PlantillaCredencialTest {

    @AfterEach
    void restaurar() {
        PlantillaCredencial.registrar(null, null);
    }

    @Test
    void sinRegistroSeUsanLasDelCodigo() {
        assertThat(PlantillaCredencial.catalogo()).isEqualTo(PlantillaCredencial.CATALOGO);
        assertThat(PlantillaCredencial.porDefecto().id()).isEqualTo("CREDENCIAL_VIRTUAL");
        assertThat(PlantillaCredencial.EXPOSITOR.fotoCircular()).isTrue();
        assertThat(PlantillaCredencial.EXPOSITOR.multilinea()).isTrue();
        assertThat(PlantillaCredencial.QR_GRANDE.fotoCircular()).isFalse();
    }

    @Test
    void elRegistroReemplazaYConservaElPorDefecto() {
        PlantillaCredencial editada = new PlantillaCredencial("EXPOSITOR", "Expositor 2027", null,
                PlantillaCredencial.EXPOSITOR.imagen(), true, false, true, PlantillaCredencial.EXPOSITOR.qr(),
                null, null, null, null, null, null, null, "STAFF", "#112233", false, false);
        PlantillaCredencial.registrar(List.of(PlantillaCredencial.QR_GRANDE, editada), "EXPOSITOR");

        assertThat(PlantillaCredencial.actual(PlantillaCredencial.EXPOSITOR).etiqueta()).isEqualTo("Expositor 2027");
        assertThat(PlantillaCredencial.porDefecto().id()).isEqualTo("EXPOSITOR");
        assertThat(PlantillaCredencial.de("credencial_virtual")).isEmpty();
        assertThat(PlantillaCredencial.oPorDefecto("NO_EXISTE").id()).isEqualTo("EXPOSITOR");
        assertThat(PlantillaCredencial.actual(PlantillaCredencial.CREDENCIAL_VIRTUAL))
                .isSameAs(PlantillaCredencial.CREDENCIAL_VIRTUAL);
    }

    @Test
    void unPorDefectoInexistenteCaeEnLaPrimera() {
        PlantillaCredencial.registrar(List.of(PlantillaCredencial.QR_GRANDE), "OTRA");
        assertThat(PlantillaCredencial.porDefecto().id()).isEqualTo("QR_GRANDE");
    }
}

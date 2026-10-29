package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.usic.uniFex.model.service.CredencialCodigoService;

class CredencialCodigoTest {

    private static CredencialCodigoService servicio() {
        CredencialCodigoService s = new CredencialCodigoService();
        ReflectionTestUtils.setField(s, "secreto",
                "secreto-de-prueba-suficientemente-largo-para-hmac-sha256");
        return s;
    }

    @Test
    void todoCodigoGeneradoVuelveASuResponsable() {
        CredencialCodigoService s = servicio();
        int conGuion = 0;
        for (long id = 1; id <= 800; id++) {
            String codigo = s.codigoDe(id);
            if (codigo.split("-").length > 3) conGuion++;
            assertThat(s.responsableDe(codigo))
                    .as("el codigo %s tiene que devolver el responsable %s", codigo, id)
                    .isEqualTo(id);
        }
        assertThat(conGuion)
                .as("con 800 codigos tiene que haber firmas con guiones dentro")
                .isGreaterThan(0);
    }

    @Test
    void elCodigoSeLeeAunqueVengaEnMinusculasOConEspacios() {
        CredencialCodigoService s = servicio();
        String codigo = s.codigoDe(42L);
        assertThat(s.responsableDe("  " + codigo.toLowerCase() + "  ")).isEqualTo(42L);
    }

    @Test
    void unaFirmaInventadaNoAbreNada() {
        CredencialCodigoService s = servicio();
        String codigo = s.codigoDe(7L);
        String[] p = codigo.split("-", 3);

        assertThat(s.responsableDe(p[0] + "-" + p[1] + "-AAAAAAAAAAAA")).isNull();
        assertThat(s.responsableDe(p[0] + "-" + Long.toString(8L, 36).toUpperCase() + "-" + p[2])).isNull();
        assertThat(s.responsableDe("XXX-" + p[1] + "-" + p[2])).isNull();
        assertThat(s.responsableDe("cualquier-cosa")).isNull();
        assertThat(s.responsableDe(null)).isNull();
    }

    @Test
    void reimprimirDaSiempreElMismoCodigo() {
        assertThat(servicio().codigoDe(123L)).isEqualTo(servicio().codigoDe(123L));
    }

    @Test
    void laUrlDelQrNoDuplicaLaBarra() {
        CredencialCodigoService s = servicio();
        String c = s.codigoDe(5L);
        assertThat(s.urlPublica("https://feria.uap.edu.bo/", c)).isEqualTo("https://feria.uap.edu.bo/credencial/" + c);
        assertThat(s.urlPublica("https://feria.uap.edu.bo", c)).isEqualTo("https://feria.uap.edu.bo/credencial/" + c);
    }
}

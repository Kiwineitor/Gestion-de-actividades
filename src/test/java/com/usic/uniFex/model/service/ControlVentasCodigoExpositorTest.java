package com.usic.uniFex.model.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ControlVentasCodigoExpositorTest {

    private static String[] codigo(Long id, String nit, String ci) {
        return ControlVentasService.codigoExpositor(id, nit, ci);
    }

    @Test
    void usaElNitCuandoEsValido() {
        assertArrayEquals(new String[] { "EXP-1006987025", "NIT" }, codigo(1L, "1006987025", "1071777"));
    }

    @Test
    void losRellenosDeNitCaenAlCiDelRepresentante() {
        for (String relleno : new String[] { "00000", "11111111", "S/N", "NO TIENE", "-", "--", "", null }) {
            assertArrayEquals(new String[] { "EXP-5701771", "CI" }, codigo(1L, relleno, "5701771"), "NIT: " + relleno);
        }
    }

    @Test
    void elMismoNumeroComoNitOComoCiEsElMismoExpositor() {
        String comoNit = codigo(1L, "4214822", null)[0];
        String comoCi = codigo(2L, "S/N", "4214822")[0];
        assertEquals("EXP-4214822", comoNit);
        assertEquals(comoNit, comoCi);
    }

    @Test
    void delCiSeTomaElNumeroSinExtensionNiComplemento() {
        assertArrayEquals(new String[] { "EXP-1234567", "CI" }, codigo(1L, null, "1234567 LP"));
        assertArrayEquals(new String[] { "EXP-1234567", "CI" }, codigo(1L, null, "1234567-1B"));
    }

    @Test
    void sinNitNiCiUtilesQuedaLaFichaDeLaEntidad() {
        assertArrayEquals(new String[] { "ENT-42", "FICHA" }, codigo(42L, "00000", "123"));
    }
}

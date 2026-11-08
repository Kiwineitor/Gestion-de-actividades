package com.usic.uniFex.model.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class MensajesWhatsAppVentaTest {

    @Test
    void usaLasDiezVariantesAntesDeRepetirYNoRepiteDosSeguidas() {
        var mensajes = new MensajesWhatsAppVenta();
        var primeraRonda = new HashSet<String>();
        String anterior = null;

        for (int i = 0; i < MensajesWhatsAppVenta.cantidadVariantes() * 5; i++) {
            String actual = mensajes.siguiente("Empresa Prueba", "Noche de Bolos", true);
            assertNotEquals(anterior, actual);
            assertTrue(actual.contains("Empresa Prueba"));
            assertTrue(actual.contains("Responde este mensaje para confirmar la entrega."));
            assertTrue(actual.contains("Registra este número para estar al tanto de las novedades de Noche de Bolos."));
            assertTrue(actual.contains("credencial"));
            assertFalse(actual.contains("FEXPO"));
            assertFalse(actual.contains("{actividad}"));
            assertFalse(actual.toLowerCase().contains("automático"));
            assertFalse(actual.toLowerCase().contains("automatizado"));
            if (i < MensajesWhatsAppVenta.cantidadVariantes()) primeraRonda.add(actual);
            anterior = actual;
        }

        assertEquals(10, MensajesWhatsAppVenta.cantidadVariantes());
        assertEquals(10, primeraRonda.size());
    }

    @Test
    void sinCredencialesSoloHablaDelRecibo() {
        var mensajes = new MensajesWhatsAppVenta();
        for (int i = 0; i < MensajesWhatsAppVenta.cantidadVariantes(); i++) {
            String actual = mensajes.siguiente("Juan Pérez", "Noche de Bolos", false);
            assertTrue(actual.contains("recibo"));
            assertFalse(actual.toLowerCase().contains("credencial"));
            assertFalse(actual.contains("QR"));
        }
    }

    @Test
    void esSeguroAnteEnviosSimultaneosYUsaRespaldos() {
        var mensajes = new MensajesWhatsAppVenta();
        var ronda = IntStream.range(0, 10).parallel()
                .mapToObj(i -> mensajes.siguiente(null, null, true)).toList();

        assertEquals(10, new HashSet<>(ronda).size());
        assertTrue(ronda.stream().allMatch(m -> m.contains("Hola cliente,") && m.contains("la actividad")));
    }
}

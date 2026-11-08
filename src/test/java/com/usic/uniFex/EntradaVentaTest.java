package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.usic.uniFex.model.service.EntradaService;
import com.usic.uniFex.model.service.EntradaService.Compra;
import com.usic.uniFex.model.service.EntradaService.Item;

@SpringBootTest
@ActiveProfiles("dev")
class EntradaVentaTest {

    @Autowired private EntradaService entradas;
    @Autowired private JdbcTemplate jdbc;

    private Long edicion;
    private Long general;
    private Long gratis;
    private Long usuario;

    @BeforeEach
    void preparar() {
        edicion = jdbc.queryForObject("""
                INSERT INTO edicion (nombre, anio, activa, plano_version, plano_sin_fondo, publica, publicacion_independiente,
                                     recibe_interesados, vende_entradas, vende_espacios, pago_oficina)
                VALUES (?, 2026, FALSE, 0, TRUE, TRUE, TRUE, TRUE, TRUE, FALSE, 'Oficina central') RETURNING id
                """, Long.class, "Prueba entradas " + UUID.randomUUID());
        general = jdbc.queryForObject("INSERT INTO entrada_tipo (id_edicion, nombre, precio, cupo, orden) VALUES (?, 'General', 50, 10, 1) RETURNING id",
                Long.class, edicion);
        gratis = jdbc.queryForObject("INSERT INTO entrada_tipo (id_edicion, nombre, precio, cupo, orden) VALUES (?, 'Cortesía', 0, NULL, 2) RETURNING id",
                Long.class, edicion);
        usuario = jdbc.queryForObject("SELECT min(id) FROM usuario", Long.class);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM entrada_compra WHERE id_edicion = ?", edicion);
        jdbc.update("DELETE FROM entrada_tipo WHERE id_edicion = ?", edicion);
        jdbc.update("DELETE FROM edicion WHERE id = ?", edicion);
    }

    private Compra compra(String celular, Long tipo, int cantidad, String modo) {
        return new Compra("Ana Prueba Rojas", "1234567", celular, modo, true, List.of(new Item(tipo, cantidad)));
    }

    @Test
    void quinceCompradoresCuponDeDiezSoloDiezEntran() throws Exception {
        CountDownLatch disparo = new CountDownLatch(1);
        List<Future<EntradaService.Resultado>> intentos = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(15)) {
            for (int i = 0; i < 15; i++) {
                String celular = "7000000" + String.format("%02d", i);
                intentos.add(pool.submit(() -> { disparo.await(); return entradas.comprarEnLinea(edicion, compra(celular, general, 1, "OFICINA"), null, "prueba"); }));
            }
            disparo.countDown();
            int ok = 0;
            for (var f : intentos) if (f.get(30, TimeUnit.SECONDS).ok()) ok++;
            assertThat(ok).isEqualTo(10);
        }
        assertThat(entradas.disponibles(edicion).get(0).get("quedan")).isEqualTo(0L);
    }

    @Test
    void aprobarGeneraUnPasePorEntradaYElIngresoAlterna() throws Exception {
        var r = entradas.comprarEnLinea(edicion, compra("71111111", general, 3, "OFICINA"), null, "prueba");
        assertThat(r.ok()).as(r.mensaje()).isTrue();
        String codigo = (String) r.datos().get("codigo");
        Long id = ((Number) r.datos().get("id")).longValue();
        assertThat((List<?>) entradas.consultar(codigo).get("pases")).isEmpty();
        assertThat(entradas.aprobar(id, usuario).ok()).isTrue();
        assertThat(entradas.aprobar(id, usuario).ok()).isFalse();
        List<?> pases = (List<?>) entradas.consultar(codigo).get("pases");
        assertThat(pases).hasSize(3);
        String pase = (String) ((Map<?, ?>) pases.get(0)).get("codigo");
        assertThat(EntradaService.esCodigoDePase(pase)).isTrue();

        assertThat(entradas.registrarIngreso(pase, usuario)).containsEntry("ok", true).containsEntry("sentido", "E");
        assertThat(entradas.registrarIngreso(pase, usuario)).containsEntry("ok", false);
        jdbc.update("UPDATE entrada_pase_acceso SET cuando = now() - interval '1 minute'");
        assertThat(entradas.registrarIngreso(pase, usuario)).containsEntry("ok", true).containsEntry("sentido", "S");
        assertThat(entradas.anular(id, "Pedido del cliente", usuario).ok()).isFalse();

        assertThat(entradas.pdfPases(codigo)).isNotEmpty();
        assertThat(entradas.recibo(codigo)).isNotEmpty();
        assertThat((BigDecimal) entradas.resumen(edicion).get("recaudado")).isEqualByComparingTo("150");
    }

    @Test
    void rechazarLiberaElCupoYLasGratisSeApruebanSolas() throws Exception {
        var r = entradas.comprarEnLinea(edicion, compra("72222222", general, 10, "OFICINA"), null, "prueba");
        assertThat(r.ok()).isTrue();
        assertThat(entradas.comprarEnLinea(edicion, compra("73333333", general, 1, "OFICINA"), null, "prueba").ok()).isFalse();
        assertThat(entradas.rechazar(((Number) r.datos().get("id")).longValue(), null, usuario).ok()).isFalse();
        assertThat(entradas.rechazar(((Number) r.datos().get("id")).longValue(), "Comprobante ilegible", usuario).ok()).isTrue();
        assertThat(entradas.comprarEnLinea(edicion, compra("73333333", general, 1, "OFICINA"), null, "prueba").ok()).isTrue();

        var libre = entradas.comprarEnLinea(edicion, compra("74444444", gratis, 2, null), null, "prueba");
        assertThat(libre.ok()).isTrue();
        assertThat(libre.datos().get("estado")).isEqualTo("APROBADA");
        assertThat((List<?>) entradas.consultar((String) libre.datos().get("codigo")).get("pases")).hasSize(2);
    }

    @Test
    void laVentaEnOficinaQuedaAprobadaYUnaActividadCerradaNoVende() throws Exception {
        var oficina = entradas.venderEnOficina(edicion, new Compra("Luis Prueba", null, "75555555", "EFECTIVO", null,
                List.of(new Item(general, 2))), null, usuario);
        assertThat(oficina.ok()).as(oficina.mensaje()).isTrue();
        assertThat(oficina.datos().get("estado")).isEqualTo("APROBADA");
        assertThat(entradas.comprarEnLinea(edicion, compra("76666666", general, 1, "COMPROBANTE"), null, "prueba").ok()).isFalse();
        jdbc.update("UPDATE edicion SET finalizada = TRUE WHERE id = ?", edicion);
        assertThat(entradas.comprarEnLinea(edicion, compra("77777777", general, 1, "OFICINA"), null, "prueba").ok()).isFalse();
        assertThat(entradas.venderEnOficina(edicion, compra("77777777", general, 1, "EFECTIVO"), null, usuario).ok()).isFalse();
    }
}

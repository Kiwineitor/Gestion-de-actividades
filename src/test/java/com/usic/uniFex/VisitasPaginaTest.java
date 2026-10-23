package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.usic.uniFex.model.service.VisitasPaginaService;

/** Comprueba el contador de la página pública sin depender de los módulos retirados. */
@SpringBootTest
@ActiveProfiles("dev")
class VisitasPaginaTest {

    private static final String PAGINA_PRUEBA = "prueba-automatica-visitas";

    @Autowired private VisitasPaginaService visitas;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM contador_visitas WHERE pagina = ?", PAGINA_PRUEBA);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM contador_visitas WHERE pagina = ?", PAGINA_PRUEBA);
    }

    @Test
    void lasVisitasSimultaneasNoSePierden() throws Exception {
        int hilos = 20;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        try {
            CountDownLatch salida = new CountDownLatch(1);
            List<Future<Long>> pendientes = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                pendientes.add(pool.submit(() -> {
                    salida.await();
                    return visitas.registrar(PAGINA_PRUEBA);
                }));
            }
            salida.countDown();
            List<Long> totales = new ArrayList<>();
            for (Future<Long> pendiente : pendientes) totales.add(pendiente.get(10, TimeUnit.SECONDS));

            assertThat(visitas.total(PAGINA_PRUEBA)).isEqualTo(hilos);
            assertThat(totales).containsExactlyInAnyOrderElementsOf(
                    LongStream.rangeClosed(1, hilos).boxed().toList());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void unaPaginaSinVisitasDaCero() {
        assertThat(visitas.total(PAGINA_PRUEBA)).isZero();
    }
}

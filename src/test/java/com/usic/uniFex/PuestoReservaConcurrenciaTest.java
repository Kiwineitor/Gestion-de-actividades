package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.service.PuestoMapaService;
import com.usic.uniFex.model.service.PuestoReservaService;

@SpringBootTest
@ActiveProfiles("dev")
class PuestoReservaConcurrenciaTest {

    @Autowired private PuestoReservaService reserva;
    @Autowired private PuestoMapaService mapa;
    @Autowired private IPuestoDao puestoDao;
    @Autowired private JdbcTemplate jdbc;

    private Long puestoId;

    @BeforeEach
    void setUp() {
        Puesto libre = puestoDao.listarActivos().stream()
                .filter(p -> Puesto.LIBRE.equals(p.getEstadoPuesto())).findFirst()
                .orElseThrow(() -> new IllegalStateException("No hay ninguna caseta LIBRE para la prueba"));
        puestoId = libre.getId();
        resetLibre();
    }

    @AfterEach
    void tearDown() {
        resetLibre();
    }

    private void resetLibre() {
        jdbc.update("UPDATE puesto SET estado_puesto='L', reservado_por_id_usuario=NULL, "
                + "reserva_expira=NULL WHERE id=?", puestoId);
    }

    @Test
    void soloUnUsuarioGanaLaCasetaBajoConcurrencia() throws Exception {
        final int hilos = 25;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch disparo = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger(0);

        List<Future<Boolean>> futuros = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            final long usuarioId = 1000L + i;
            Callable<Boolean> tarea = () -> {
                listos.countDown();
                disparo.await();
                boolean gano = reserva.reservar(puestoId, usuarioId);
                if (gano) exitos.incrementAndGet();
                return gano;
            };
            futuros.add(pool.submit(tarea));
        }

        listos.await();
        disparo.countDown();
        for (Future<Boolean> f : futuros) f.get();
        pool.shutdown();

        assertThat(exitos.get())
                .as("solo un usuario debe ganar la caseta")
                .isEqualTo(1);

        Puesto p = puestoDao.findById(puestoId).orElseThrow();
        assertThat(p.getEstadoPuesto()).isEqualTo(Puesto.EN_TRAMITE);
        assertThat(p.getReservadoPorIdUsuario()).isNotNull();
        assertThat(p.getReservaExpira()).isNotNull();
    }

    @Test
    void bloqueoSoloDesdeLibreYDesbloqueoSoloDesdeBloqueada() {
        assertThat(mapa.bloquear(puestoId, 3000L)).isTrue();
        Puesto p = puestoDao.findById(puestoId).orElseThrow();
        assertThat(p.getEstadoPuesto()).isEqualTo(Puesto.BLOQUEADO);

        assertThat(reserva.reservar(puestoId, 3001L)).isFalse();

        assertThat(mapa.bloquear(puestoId, 3000L)).isFalse();

        assertThat(mapa.desbloquear(puestoId, 3000L)).isTrue();
        p = puestoDao.findById(puestoId).orElseThrow();
        assertThat(p.getEstadoPuesto()).isEqualTo(Puesto.LIBRE);
        assertThat(reserva.reservar(puestoId, 3001L)).isTrue();
        reserva.liberar(puestoId, 3001L);

        assertThat(mapa.desbloquear(puestoId, 3000L)).isFalse();
    }

    @Test
    void elBarridoLiberaLaReservaVencida() {
        assertThat(reserva.reservar(puestoId, 5000L)).isTrue();
        jdbc.update("UPDATE puesto SET reserva_expira = now() - interval '1 minute' WHERE id=?", puestoId);

        List<Long> liberadas = reserva.liberarVencidas();

        assertThat(liberadas).as("debe liberar la caseta vencida").contains(puestoId);
        Puesto p = puestoDao.findById(puestoId).orElseThrow();
        assertThat(p.getEstadoPuesto()).isEqualTo(Puesto.LIBRE);
        assertThat(p.getReservadoPorIdUsuario()).isNull();
        assertThat(p.getReservaExpira()).isNull();
    }

    @Test
    void elCarritoNoLeRobaCasetasAOtroVendedor() {
        final long vendedorA = 6000L;
        final long vendedorB = 6001L;

        PuestoReservaService.ResultadoLote r1 = reserva.agregarAlCarrito(List.of(puestoId), vendedorA);
        assertThat(r1.logradas()).containsExactly(puestoId);
        assertThat(r1.todoOk()).isTrue();

        PuestoReservaService.ResultadoLote r2 = reserva.agregarAlCarrito(List.of(puestoId), vendedorB);
        assertThat(r2.logradas()).isEmpty();
        assertThat(r2.rechazadas()).containsExactly(puestoId);
        assertThat(puestoDao.findById(puestoId).orElseThrow().getReservadoPorIdUsuario())
                .isEqualTo(vendedorA);

        assertThat(reserva.quitarDelCarrito(List.of(puestoId), vendedorB).logradas()).isEmpty();
        assertThat(puestoDao.findById(puestoId).orElseThrow().getEstadoPuesto())
                .isEqualTo(Puesto.EN_TRAMITE);

        assertThat(reserva.agregarAlCarrito(List.of(puestoId), vendedorA).todoOk()).isTrue();

        assertThat(reserva.quitarDelCarrito(List.of(puestoId), vendedorA).logradas())
                .containsExactly(puestoId);
        assertThat(puestoDao.findById(puestoId).orElseThrow().getEstadoPuesto())
                .isEqualTo(Puesto.LIBRE);
    }

    @Test
    void elCarritoConservaLoQueSiConsiguioAunqueUnaCasetaSePierda() {
        Long ajena = puestoDao.listarPuestos().stream()
                .map(Puesto::getId)
                .filter(id -> !id.equals(puestoId))
                .findFirst()
                .orElse(null);
        org.junit.jupiter.api.Assumptions.assumeTrue(ajena != null,
                "hacen falta al menos dos casetas libres");

        final long otro = 7000L;
        final long vendedor = 7001L;
        assertThat(reserva.agregarAlCarrito(List.of(ajena), otro).todoOk()).isTrue();
        try {
            PuestoReservaService.ResultadoLote r =
                    reserva.agregarAlCarrito(List.of(puestoId, ajena), vendedor);

            assertThat(r.todoOk()).isFalse();
            assertThat(r.logradas()).containsExactly(puestoId);
            assertThat(r.rechazadas()).containsExactly(ajena);
        } finally {
            reserva.quitarDelCarrito(List.of(ajena), otro);
            reserva.quitarDelCarrito(List.of(puestoId), vendedor);
        }
    }

    @Test
    void soloUnUsuarioOcupaLaCasetaBajoConcurrencia() throws Exception {
        final int hilos = 25;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch disparo = new CountDownLatch(1);
        AtomicInteger exitos = new AtomicInteger(0);

        List<Future<Boolean>> futuros = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            final long usuarioId = 2000L + i;
            futuros.add(pool.submit(() -> {
                listos.countDown();
                disparo.await();
                boolean ok = reserva.ocupar(puestoId, usuarioId);
                if (ok) exitos.incrementAndGet();
                return ok;
            }));
        }
        listos.await();
        disparo.countDown();
        for (Future<Boolean> f : futuros) f.get();
        pool.shutdown();

        assertThat(exitos.get()).as("solo un registro debe ocupar la caseta").isEqualTo(1);
        Puesto p = puestoDao.findById(puestoId).orElseThrow();
        assertThat(p.getEstadoPuesto()).isEqualTo(Puesto.OCUPADO);
    }
}

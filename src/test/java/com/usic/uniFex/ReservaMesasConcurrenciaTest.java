package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.usic.uniFex.model.service.AvisoReservaMesaService;
import com.usic.uniFex.model.service.MesaPaseService;
import com.usic.uniFex.model.service.FileStorageService;
import com.usic.uniFex.model.service.MesaService;
import com.usic.uniFex.model.service.MesaService.Cliente;
import com.usic.uniFex.model.service.MesaService.Resultado;

@SpringBootTest
@ActiveProfiles("dev")
class ReservaMesasConcurrenciaTest {
    @Autowired private MesaService mesas;
    @Autowired private MesaPaseService pases;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private AvisoReservaMesaService avisos;
    @MockitoBean private FileStorageService archivos;

    private Long evento;
    private List<Long> ids;

    @BeforeEach
    void preparar() {
        evento = jdbc.queryForObject("""
                INSERT INTO mesa_evento (nombre, local_nombre, fecha_inicio, publicado, pago_qr_archivo)
                VALUES ('Prueba de mesas', 'Prueba', now() + interval '1 day', TRUE, 'qr-prueba.png')
                RETURNING id
                """, Long.class);
        ids = jdbc.queryForList("""
                INSERT INTO mesa (id_evento, codigo, capacidad, precio)
                SELECT ?, 'M' || n, 10, 100 FROM generate_series(1, 4) n RETURNING id
                """, Long.class, evento);
    }

    @AfterEach
    void limpiar() {
        if (evento == null) return;
        jdbc.update("UPDATE mesa SET id_reserva = NULL WHERE id_evento = ?", evento);
        jdbc.update("DELETE FROM mesa_pase WHERE id_reserva IN (SELECT id FROM mesa_reserva WHERE id_evento = ?)", evento);
        jdbc.update("DELETE FROM mesa_reserva WHERE id_evento = ?", evento);
        jdbc.update("DELETE FROM mesa WHERE id_evento = ?", evento);
        jdbc.update("DELETE FROM mesa_evento WHERE id = ?", evento);
    }

    @Test
    void unCelularNoPuedeApartarCuatroMesasConSolicitudesSimultaneas() throws Exception {
        Cliente primero = new Cliente("Persona Uno", null, "123456", "70000001", null, null);
        Cliente segundo = new Cliente("Persona Dos", null, "654321", "70000001", null, null);
        CountDownLatch disparo = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> {
                disparo.await();
                return mesas.reservar(evento, ids.subList(0, 2), primero, "prueba-a");
            });
            var b = pool.submit(() -> {
                disparo.await();
                return mesas.reservar(evento, ids.subList(2, 4), segundo, "prueba-b");
            });
            disparo.countDown();
            Resultado ra = a.get(10, TimeUnit.SECONDS);
            Resultado rb = b.get(10, TimeUnit.SECONDS);
            assertThat(List.of(ra, rb)).filteredOn(Resultado::ok).hasSize(1);
            String codigo = (String) (ra.ok() ? ra : rb).datos().get("codigo");
            var reserva = mesas.consultarReserva(codigo);
            assertThat((BigDecimal) reserva.get("monto")).isEqualByComparingTo("200");
            assertThat(reserva.get("personas")).isEqualTo(20);
            assertThat(jdbc.queryForList("SELECT DISTINCT estado FROM mesa WHERE id_evento = ?", String.class, evento))
                    .containsExactlyInAnyOrder("T", "L");
            assertThat(mesas.cancelarPorCliente(codigo).ok()).isTrue();
            assertThat(jdbc.queryForList("SELECT estado FROM mesa WHERE id_evento = ?", String.class, evento))
                    .containsOnly("L");
        }
    }

    @Test
    void ambasMesasCompartenComprobanteYUnSoloRetiro() throws Exception {
        Cliente titular = new Cliente("Persona Titular", null, "123456", "70000002", null, null);
        when(archivos.save(any(), eq(FileStorageService.Bucket.MESAS), anyString())).thenReturn("comprobante-prueba.pdf");
        var apartada = mesas.reservar(evento, ids.subList(0, 2), titular, "prueba-pago");
        assertThat(apartada.ok()).isTrue();
        String codigo = (String) apartada.datos().get("codigo");
        Long id = jdbc.queryForObject("SELECT id FROM mesa_reserva WHERE codigo = ?", Long.class, codigo);
        var comprobante = new MockMultipartFile("archivo", "pago.pdf", "application/pdf", new byte[] { 1 });
        assertThat(mesas.adjuntarComprobante(codigo, comprobante).ok()).isTrue();
        assertThat(mesas.reservar(evento, List.of(ids.get(2)), titular, "prueba-pago").ok()).isFalse();
        Long usuario = jdbc.queryForObject("SELECT min(id) FROM usuario", Long.class);
        assertThat(mesas.confirmar(id, usuario).ok()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mesa WHERE id_reserva = ? AND estado = 'O'", Integer.class, id))
                .isEqualTo(2);
        assertThat(mesas.consultarReserva(codigo).get("pases")).isEqualTo(List.of());
        assertThat(mesas.regenerarPases(id, 4, null, usuario).ok()).isTrue();
        assertThat(pases.deReserva(id)).hasSize(4);
        assertThat(pases.imagen(id, 1)).isNotEmpty();
        assertThat(pases.pdf(codigo)).isNotEmpty();
        assertThat(mesas.registrarIngreso(codigo + "-2", usuario).ok()).isFalse();
        assertThat(mesas.registrarIngreso(codigo, usuario).ok()).isTrue();
        assertThat(mesas.registrarIngreso(codigo, usuario).ok()).isFalse();
        assertThat(mesas.reservar(evento, List.of(ids.get(2)), titular, "prueba-pago").ok()).isTrue();
    }
}

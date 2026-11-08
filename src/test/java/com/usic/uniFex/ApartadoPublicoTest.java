package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.usic.uniFex.model.dto.InteresadoStandRequest;
import com.usic.uniFex.model.service.ApartadoPublicoService;
import com.usic.uniFex.model.service.InteresadoStandService;
import com.usic.uniFex.model.service.PuestoReservaService;
import com.usic.uniFex.model.service.WhatsAppService;

@SpringBootTest
@ActiveProfiles("dev")
class ApartadoPublicoTest {

    @Autowired private ApartadoPublicoService apartados;
    @Autowired private InteresadoStandService solicitudes;
    @Autowired private PuestoReservaService reservas;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private WhatsAppService whatsApp;

    private final String nombre = "Prueba apartado " + UUID.randomUUID();
    private final String comprador = "COMPRADOR APARTADO " + UUID.randomUUID().toString().replaceAll("[^a-z]", "").toUpperCase();
    private Long edicion;
    private Long categoria;
    private List<Long> puestos;

    @BeforeEach
    void preparar() {
        edicion = jdbc.queryForObject("""
                INSERT INTO edicion (nombre, anio, activa, plano_version, plano_sin_fondo, publica, publicacion_independiente,
                                     recibe_interesados, minutos_bloqueo, comprador, tipo_espacio)
                VALUES (?, 2026, FALSE, 0, TRUE, TRUE, TRUE, TRUE, 5, 'PERSONA', 'MESA') RETURNING id
                """, Long.class, nombre);
        categoria = jdbc.queryForObject("""
                INSERT INTO categoria (nombre, color, forma, precio_base, id_edicion, personas_por_puesto, "_estado", "_fecha_registro")
                VALUES ('Mesa VIP', '#7c3aed', 'circulo', 500, ?, 6, 'A', now()) RETURNING id
                """, Long.class, edicion);
        puestos = jdbc.queryForList("""
                INSERT INTO puesto (id_categoria, codigo, tamano, estado_puesto, version, mapa_x, mapa_y, "_estado", "_fecha_registro")
                SELECT ?, n::text, '1x1', 'L', 0, 0.1 * n, 0.5, 'A', now() FROM generate_series(1, 5) n RETURNING id
                """, Long.class, categoria);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM auditoria WHERE tabla = 'inscripcion' AND id_registro IN (SELECT id FROM inscripcion WHERE id_edicion = ?)", edicion);
        jdbc.update("UPDATE interesado_stand SET id_inscripcion = NULL WHERE id_categoria = ?", categoria);
        jdbc.update("DELETE FROM inscripcion_puesto WHERE id_inscripcion IN (SELECT id FROM inscripcion WHERE id_edicion = ?)", edicion);
        List<Long> entidades = jdbc.queryForList("SELECT id_entidad FROM inscripcion WHERE id_edicion = ?", Long.class, edicion);
        jdbc.update("DELETE FROM inscripcion WHERE id_edicion = ?", edicion);
        for (Long e : entidades) {
            List<Long> personas = jdbc.queryForList("SELECT id_persona FROM responsable WHERE id_entidad = ?", Long.class, e);
            jdbc.update("DELETE FROM responsable WHERE id_entidad = ?", e);
            for (Long p : personas) jdbc.update("DELETE FROM persona WHERE id = ?", p);
            jdbc.update("DELETE FROM entidad WHERE id = ?", e);
        }
        jdbc.update("DELETE FROM interesado_stand WHERE id_categoria = ?", categoria);
        jdbc.update("DELETE FROM puesto WHERE id_categoria = ?", categoria);
        jdbc.update("DELETE FROM categoria WHERE id = ?", categoria);
        jdbc.update("DELETE FROM edicion WHERE id = ?", edicion);
    }

    private String estado(Long puesto) {
        return jdbc.queryForObject("SELECT estado_puesto FROM puesto WHERE id = ?", String.class, puesto);
    }

    private InteresadoStandRequest pedido(Long puesto, String token) {
        return new InteresadoStandRequest(comprador, "71234567", comprador, "Particular", categoria, puesto, null, "1234567", token);
    }

    @Test
    void veinticincoClientesSoloUnoApartaLaMesa() throws Exception {
        Long mesa = puestos.get(0);
        CountDownLatch disparo = new CountDownLatch(1);
        List<Future<ApartadoPublicoService.Apartado>> intentos = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(25)) {
            for (int i = 0; i < 25; i++) {
                String origen = "cliente-" + i;
                intentos.add(pool.submit(() -> { disparo.await(); return apartados.apartar(mesa, origen); }));
            }
            disparo.countDown();
            int ganadores = 0;
            for (var f : intentos) if (f.get(20, TimeUnit.SECONDS).ok()) ganadores++;
            assertThat(ganadores).isEqualTo(1);
        }
        assertThat(estado(mesa)).isEqualTo("T");
        assertThat(reservas.reservar(mesa, 1L)).isFalse();
    }

    @Test
    void elApartadoSeFijaAlEnviarYSeLiberaAlRechazar() throws Exception {
        Long mesa = puestos.get(1);
        var a = apartados.apartar(mesa, "cliente-a");
        assertThat(a.ok()).isTrue();
        assertThat(solicitudes.registrar(pedido(mesa, "token-ajeno"), null).ok()).isFalse();
        var r = solicitudes.registrar(pedido(mesa, a.token()), null);
        assertThat(r.ok()).as(r.mensaje()).isTrue();
        assertThat(jdbc.queryForObject("SELECT reserva_expira IS NULL FROM puesto WHERE id = ?", Boolean.class, mesa)).isTrue();
        assertThat(apartados.soltar(mesa, a.token())).isFalse();
        assertThat(estado(mesa)).isEqualTo("T");

        Long id = r.interesado().getId();
        assertThat(solicitudes.cambiarEstado(id, "DESCARTADO", "Comprobante ilegible", null).ok()).isTrue();
        assertThat(estado(mesa)).isEqualTo("L");
        assertThat(solicitudes.cambiarEstado(id, "NUEVO", null, null).ok()).isTrue();
        assertThat(estado(mesa)).isEqualTo("T");
    }

    @Test
    void aprobarConvierteElApartadoEnVentaDeEstaActividad() throws Exception {
        Long mesa = puestos.get(2);
        var a = apartados.apartar(mesa, "cliente-b");
        var r = solicitudes.registrar(pedido(mesa, a.token()), null);
        assertThat(r.ok()).as(r.mensaje()).isTrue();
        Long tipo = jdbc.queryForObject("SELECT id FROM tipo_entidad WHERE nombre = 'PERSONA PARTICULAR'", Long.class);
        Long usuario = jdbc.queryForObject("SELECT min(id) FROM usuario", Long.class);
        var aprobada = solicitudes.aprobarComoVenta(r.interesado().getId(),
                new InteresadoStandService.Aprobacion(null, "1234567", tipo, null, null, false), usuario);
        assertThat(aprobada.ok()).as(aprobada.mensaje()).isTrue();
        assertThat(estado(mesa)).isEqualTo("O");
        assertThat(jdbc.queryForObject("SELECT id_edicion FROM inscripcion WHERE id = ?", Long.class,
                aprobada.interesado().getInscripcionId())).isEqualTo(edicion);
    }

    @Test
    void elApartadoVencidoVuelveALibreYNoSePuedeEnviar() throws Exception {
        Long mesa = puestos.get(3);
        var a = apartados.apartar(mesa, "cliente-c");
        jdbc.update("UPDATE puesto SET reserva_expira = now() - interval '1 second' WHERE id = ?", mesa);
        assertThat(reservas.liberarVencidas()).contains(mesa);
        assertThat(estado(mesa)).isEqualTo("L");
        assertThat(solicitudes.registrar(pedido(mesa, a.token()), null).ok()).isFalse();
    }

    @Test
    void unaActividadFinalizadaNoSeApartaYHayTopePorCliente() {
        for (int i = 0; i < ApartadoPublicoService.MAXIMO_POR_ORIGEN; i++) {
            assertThat(apartados.apartar(puestos.get(i), "mismo-cliente").ok()).isTrue();
        }
        assertThat(apartados.apartar(puestos.get(3), "mismo-cliente").ok()).isFalse();
        jdbc.update("UPDATE edicion SET finalizada = TRUE WHERE id = ?", edicion);
        assertThat(apartados.apartar(puestos.get(4), "otro-cliente").ok()).isFalse();
    }
}

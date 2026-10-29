package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.usic.uniFex.model.service.PuestoMapaService;
import com.usic.uniFex.model.service.PuestoReservaService;

@SpringBootTest
@ActiveProfiles("dev")
class PuestoAnulacionTest {

    private static final Long USUARIO = 1L;

    @Autowired private PuestoMapaService mapaService;
    @Autowired private PuestoReservaService reservaService;
    @Autowired private JdbcTemplate jdbc;

    private Long puestoLibre;
    private Long puestoVendido;

    @BeforeEach
    void setUp() {
        puestoLibre = jdbc.queryForObject(
                "SELECT p.id FROM puesto p WHERE p.estado_puesto='L' AND p._estado='A' "
                + "AND NOT EXISTS (SELECT 1 FROM inscripcion_puesto ip WHERE ip.id_puesto=p.id) LIMIT 1",
                Long.class);

        puestoVendido = jdbc.queryForList(
                "SELECT p.id FROM puesto p WHERE p.estado_puesto='L' AND p._estado='A' "
                + "AND EXISTS (SELECT 1 FROM inscripcion_puesto ip WHERE ip.id_puesto=p.id) LIMIT 1",
                Long.class).stream().findFirst().orElse(null);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("UPDATE puesto SET _estado='A', estado_puesto='L', "
                + "reservado_por_id_usuario=NULL, reserva_expira=NULL WHERE id=?", puestoLibre);
    }

    private String estadoRegistro(Long id) {
        return jdbc.queryForObject("SELECT _estado FROM puesto WHERE id=?", String.class, id);
    }

    private String estadoPuesto(Long id) {
        return jdbc.queryForObject("SELECT estado_puesto FROM puesto WHERE id=?", String.class, id);
    }

    @Test
    void anularUnaCasetaLibreSinVentasLaDaDeBaja() {
        assertThat(mapaService.anular(puestoLibre, USUARIO)).isTrue();

        assertThat(estadoRegistro(puestoLibre)).as("_estado pasa a anulado").isEqualTo("X");
        assertThat(estadoPuesto(puestoLibre)).as("el sitio viejo deja de ofrecerla").isEqualTo("X");
        assertThat(jdbc.queryForObject("SELECT mapa_x FROM puesto WHERE id=?", Double.class, puestoLibre))
                .as("sale del plano").isNull();
    }

    @Test
    void anularDosVecesLaMismaCasetaSoloCuentaUna() {
        assertThat(mapaService.anular(puestoLibre, USUARIO)).isTrue();
        assertThat(mapaService.anular(puestoLibre, USUARIO))
                .as("una caseta ya anulada no se vuelve a anular").isFalse();
    }

    @Test
    void noSePuedeAnularUnaCasetaConVentas() {
        org.junit.jupiter.api.Assumptions.assumeTrue(puestoVendido != null,
                "no hay ninguna caseta con ventas en la copia local (base reiniciada)");
        assertThat(mapaService.anular(puestoVendido, USUARIO))
                .as("borrarla destruiria historial de inscripcion_puesto").isFalse();
    }

    @Test
    void noSePuedeAnularUnaCasetaReservada() {
        assertThat(reservaService.reservar(puestoLibre, USUARIO)).isTrue();
        assertThat(mapaService.anular(puestoLibre, USUARIO))
                .as("un vendedor la tiene en tramite").isFalse();
        assertThat(estadoRegistro(puestoLibre)).isEqualTo("A");
    }
}

package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.usic.uniFex.model.service.CobrosService;
import com.usic.uniFex.model.service.PuestoReservaService;
import com.usic.uniFex.model.service.PuntoVentaService;
import com.usic.uniFex.model.service.PuntoVentaService.VentaEspacios;
import com.usic.uniFex.model.service.WhatsAppService;

@SpringBootTest
@ActiveProfiles("dev")
class PuntoVentaTest {

    @Autowired private PuntoVentaService puntoVenta;
    @Autowired private PuestoReservaService reservas;
    @Autowired private CobrosService cobros;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private WhatsAppService whatsApp;

    private Long edicion;
    private Long categoria;
    private List<Long> puestos;
    private Long usuario;

    @BeforeEach
    void preparar() {
        edicion = jdbc.queryForObject("""
                INSERT INTO edicion (nombre, anio, activa, plano_version, plano_sin_fondo, publica, publicacion_independiente,
                                     recibe_interesados, vende_espacios, comprador, tipo_espacio)
                VALUES (?, 2026, FALSE, 0, TRUE, TRUE, TRUE, TRUE, TRUE, 'PERSONA', 'MESA') RETURNING id
                """, Long.class, "Prueba punto de venta " + UUID.randomUUID());
        categoria = jdbc.queryForObject("""
                INSERT INTO categoria (nombre, color, forma, precio_base, id_edicion, personas_por_puesto, "_estado", "_fecha_registro")
                VALUES ('Mesa VIP', '#7c3aed', 'circulo', 500, ?, 4, 'A', now()) RETURNING id
                """, Long.class, edicion);
        puestos = jdbc.queryForList("""
                INSERT INTO puesto (id_categoria, codigo, tamano, estado_puesto, version, mapa_x, mapa_y, precio, "_estado", "_fecha_registro")
                SELECT ?, n::text, '1x1', 'L', 0, 0.2 * n, 0.5, 500, 'A', now() FROM generate_series(1, 2) n RETURNING id
                """, Long.class, categoria);
        usuario = jdbc.queryForObject("SELECT min(id) FROM usuario", Long.class);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM auditoria WHERE tabla = 'inscripcion' AND id_registro IN (SELECT id FROM inscripcion WHERE id_edicion = ?)", edicion);
        jdbc.update("DELETE FROM inscripcion_puesto WHERE id_inscripcion IN (SELECT id FROM inscripcion WHERE id_edicion = ?)", edicion);
        List<Long> entidades = jdbc.queryForList("SELECT id_entidad FROM inscripcion WHERE id_edicion = ?", Long.class, edicion);
        jdbc.update("DELETE FROM inscripcion WHERE id_edicion = ?", edicion);
        for (Long e : entidades) {
            List<Long> personas = jdbc.queryForList("SELECT id_persona FROM responsable WHERE id_entidad = ?", Long.class, e);
            jdbc.update("DELETE FROM responsable WHERE id_entidad = ?", e);
            for (Long p : personas) jdbc.update("DELETE FROM persona WHERE id = ?", p);
            jdbc.update("DELETE FROM entidad WHERE id = ?", e);
        }
        jdbc.update("DELETE FROM puesto WHERE id_categoria = ?", categoria);
        jdbc.update("DELETE FROM categoria_opcion WHERE id_categoria = ?", categoria);
        jdbc.update("DELETE FROM categoria WHERE id = ?", categoria);
        jdbc.update("DELETE FROM edicion WHERE id = ?", edicion);
    }

    private VentaEspacios venta(Long puesto, String medio) {
        return new VentaEspacios("Pedro Prueba Rojas", "4445556", "71110000", null, null, null, null, medio,
                List.of(new PuntoVentaService.Persona("Acompañante Uno", "1112223", null)), List.of(puesto));
    }

    @Test
    void ventaEnEfectivoQuedaCobradaSinFotoYEnSuActividad() throws Exception {
        Long mesa = puestos.get(0);
        assertThat(reservas.reservar(mesa, usuario)).isTrue();
        var r = puntoVenta.venderEspacios(venta(mesa, "EFECTIVO"), null, usuario);
        assertThat(r.ok()).as(r.mensaje()).isTrue();
        Long inscripcion = ((Number) r.datos().get("inscripcionId")).longValue();
        assertThat(jdbc.queryForObject("SELECT estado_puesto FROM puesto WHERE id = ?", String.class, mesa)).isEqualTo("O");
        Map<String, Object> i = jdbc.queryForMap("SELECT id_edicion, cobro_efectivo FROM inscripcion WHERE id = ?", inscripcion);
        assertThat(((Number) i.get("id_edicion")).longValue()).isEqualTo(edicion);
        assertThat(i.get("cobro_efectivo")).isEqualTo(true);
        assertThat(jdbc.queryForObject("""
                SELECT t.nombre FROM inscripcion i JOIN entidad e ON e.id = i.id_entidad JOIN tipo_entidad t ON t.id = e.id_tipo_entidad
                 WHERE i.id = ?
                """, String.class, inscripcion)).isEqualTo("PERSONA PARTICULAR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM responsable WHERE id_entidad = (SELECT id_entidad FROM inscripcion WHERE id = ?)",
                Integer.class, inscripcion)).isEqualTo(2);

        Map<String, Object> resumen = cobros.resumen(edicion);
        assertThat((BigDecimal) resumen.get("recaudado")).isEqualByComparingTo("500");
        assertThat((BigDecimal) resumen.get("porCobrar")).isEqualByComparingTo("0");

        assertThatThrownBy(() -> puntoVenta.venderEspacios(venta(mesa, "QR"), null, usuario))
                .isInstanceOf(com.usic.uniFex.model.service.RegistroVentaService.CasetaNoDisponibleException.class);
    }

    @Test
    void ventaPorQrSinFotoQuedaPorCobrarYSinMedioSeRechaza() throws Exception {
        assertThat(puntoVenta.venderEspacios(venta(puestos.get(1), "TARJETA"), null, usuario).ok()).isFalse();
        var r = puntoVenta.venderEspacios(venta(puestos.get(1), "QR"), null, usuario);
        assertThat(r.ok()).as(r.mensaje()).isTrue();
        assertThat((BigDecimal) cobros.resumen(edicion).get("porCobrar")).isEqualByComparingTo("500");
    }
}

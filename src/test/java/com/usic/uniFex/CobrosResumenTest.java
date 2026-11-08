package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import com.usic.uniFex.model.service.EntradaService;
import com.usic.uniFex.model.service.RegistroVentaService;
import com.usic.uniFex.model.service.RegistroVentaService.DatosPersona;
import com.usic.uniFex.model.service.RegistroVentaService.NuevaVenta;
import com.usic.uniFex.model.service.WhatsAppService;

@SpringBootTest
@ActiveProfiles("dev")
class CobrosResumenTest {

    @Autowired private CobrosService cobros;
    @Autowired private EntradaService entradas;
    @Autowired private RegistroVentaService registro;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private WhatsAppService whatsApp;

    private Long edicion;
    private Long categoria;
    private Long tipo;
    private List<Long> puestos;
    private Long usuario;

    @BeforeEach
    void preparar() {
        edicion = jdbc.queryForObject("""
                INSERT INTO edicion (nombre, anio, activa, plano_version, plano_sin_fondo, publica, publicacion_independiente,
                                     recibe_interesados, vende_entradas, vende_espacios, pago_oficina)
                VALUES (?, 2026, FALSE, 0, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, 'Oficina') RETURNING id
                """, Long.class, "Prueba cobros " + UUID.randomUUID());
        categoria = jdbc.queryForObject("""
                INSERT INTO categoria (nombre, color, forma, precio_base, id_edicion, personas_por_puesto, "_estado", "_fecha_registro")
                VALUES ('Stand', '#2563eb', 'cuadrado', 100, ?, 2, 'A', now()) RETURNING id
                """, Long.class, edicion);
        puestos = jdbc.queryForList("""
                INSERT INTO puesto (id_categoria, codigo, tamano, estado_puesto, version, mapa_x, mapa_y, "_estado", "_fecha_registro")
                SELECT ?, n::text, '3x3', 'L', 0, 0.2 * n, 0.5, 'A', now() FROM generate_series(1, 3) n RETURNING id
                """, Long.class, categoria);
        tipo = jdbc.queryForObject("INSERT INTO entrada_tipo (id_edicion, nombre, precio, cupo) VALUES (?, 'General', 50, 10) RETURNING id",
                Long.class, edicion);
        usuario = jdbc.queryForObject("SELECT min(id) FROM usuario", Long.class);
        jdbc.update("UPDATE puesto SET precio = 100 WHERE id_categoria = ?", categoria);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM entrada_compra WHERE id_edicion = ?", edicion);
        jdbc.update("DELETE FROM entrada_tipo WHERE id_edicion = ?", edicion);
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

    @Test
    void sumaEspaciosYEntradasContraLaMeta() throws Exception {
        Long tipoEntidad = jdbc.queryForObject("SELECT id FROM tipo_entidad ORDER BY id LIMIT 1", Long.class);
        var venta = registro.registrar(new NuevaVenta("NEGOCIO COBROS PRUEBA", null, "Rubro", "Rubro", "Rosa Prueba", "5554443",
                "71112222", tipoEntidad, LocalDate.now(), LocalDate.now(),
                List.of(new DatosPersona("ROSA", "PRUEBA", "", "5554443", null, "71112222")),
                null, null, true, List.of(puestos.get(0))), usuario);
        assertThat(venta.ok()).as(venta.mensaje()).isTrue();

        var oficina = entradas.venderEnOficina(edicion, new EntradaService.Compra("Juan Prueba", null, "72223333", "EFECTIVO", null,
                List.of(new EntradaService.Item(tipo, 2))), null, usuario);
        assertThat(oficina.ok()).as(oficina.mensaje()).isTrue();
        var enLinea = entradas.comprarEnLinea(edicion, new EntradaService.Compra("Ana Prueba", "1234567", "73334444", "OFICINA", true,
                List.of(new EntradaService.Item(tipo, 1))), null, "prueba");
        assertThat(enLinea.ok()).as(enLinea.mensaje()).isTrue();

        Map<String, Object> r = cobros.resumen(edicion);
        assertThat((BigDecimal) r.get("meta")).isEqualByComparingTo("800");
        assertThat((BigDecimal) r.get("vendido")).isEqualByComparingTo("200");
        assertThat((BigDecimal) r.get("recaudado")).isEqualByComparingTo("100");
        assertThat((BigDecimal) r.get("porCobrar")).isEqualByComparingTo("100");
        assertThat(r.get("porValidar")).isEqualTo(1L);
        assertThat((BigDecimal) r.get("porValidarMonto")).isEqualByComparingTo("50");
        assertThat((BigDecimal) r.get("porVender")).isEqualByComparingTo("550");
        assertThat((BigDecimal) r.get("avance")).isEqualByComparingTo("12.5");
        assertThat((List<?>) r.get("porMedio")).isNotEmpty();
        assertThat(cobros.ventas(edicion)).hasSize(2);
    }
}

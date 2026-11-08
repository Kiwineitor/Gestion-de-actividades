package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.usic.uniFex.model.entity.Edicion;
import com.usic.uniFex.model.service.ActividadRespaldoService;
import com.usic.uniFex.model.service.ActividadService;
import com.usic.uniFex.model.service.ConfiguracionActividadService;
import com.usic.uniFex.model.service.ConfiguracionActividadService.CambioEstado;

@SpringBootTest
@ActiveProfiles("dev")
class CicloActividadTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired ActividadService actividades;
    @Autowired ActividadRespaldoService respaldos;
    @Autowired ConfiguracionActividadService configuracion;

    private final String marca = "Ciclo " + System.nanoTime();
    private final List<Long> ediciones = new ArrayList<>();
    private Long original;
    private Long categoria;

    @BeforeEach
    void preparar() {
        original = jdbc.queryForObject("""
                INSERT INTO edicion (nombre, anio, activa, publica, publicacion_independiente, lugar, vende_espacios, recibe_interesados)
                VALUES (?, 2026, FALSE, TRUE, TRUE, 'Salón', TRUE, TRUE) RETURNING id
                """, Long.class, marca);
        ediciones.add(original);
        jdbc.update("INSERT INTO edicion_dia (id_edicion, fecha) VALUES (?, DATE '2026-12-12')", original);
        jdbc.update("INSERT INTO entrada_tipo (id_edicion, nombre, precio) VALUES (?, 'General', 50)", original);
        categoria = jdbc.queryForObject("""
                INSERT INTO categoria (nombre, id_edicion, "_estado", precio_base, color, forma, tamano_mapa)
                VALUES ('Mesa VIP', ?, 'A', 100, '#a855f7', 'circulo', 0.03) RETURNING id
                """, Long.class, original);
        jdbc.update("INSERT INTO categoria_opcion (id_categoria, nombre, precio, predeterminada) VALUES (?, 'Mesa VIP', 100, TRUE)", categoria);
        for (int i = 1; i <= 3; i++) {
            jdbc.update("""
                    INSERT INTO puesto (id_categoria, codigo, estado_puesto, "_estado", mapa_x, mapa_y, mapa_escala, mapa_rotacion, version)
                    VALUES (?, ?, ?, 'A', ?, 0.5, 1.2, 15, 3)
                    """, categoria, String.valueOf(i), i == 2 ? "O" : "L", 0.2 * i);
        }
        jdbc.update("""
                INSERT INTO plano_texto (id_edicion, contenido, mapa_x, mapa_y, fondo, ancho, alto, "_estado")
                VALUES (?, 'ESCENARIO', 0.5, 0.1, '#7c5cff', 0.3, 0.1, 'A')
                """, original);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM actividad_respaldo WHERE nombre LIKE ?", marca + "%");
        for (Long id : ediciones) {
            jdbc.update("DELETE FROM interesado_stand WHERE id_categoria IN (SELECT id FROM categoria WHERE id_edicion = ?)", id);
            jdbc.update("DELETE FROM puesto WHERE id_categoria IN (SELECT id FROM categoria WHERE id_edicion = ?)", id);
            jdbc.update("DELETE FROM categoria_opcion WHERE id_categoria IN (SELECT id FROM categoria WHERE id_edicion = ?)", id);
            jdbc.update("DELETE FROM categoria WHERE id_edicion = ?", id);
            jdbc.update("DELETE FROM plano_texto WHERE id_edicion = ?", id);
            jdbc.update("DELETE FROM entrada_tipo WHERE id_edicion = ?", id);
            jdbc.update("DELETE FROM edicion_dia WHERE id_edicion = ?", id);
            jdbc.update("DELETE FROM edicion WHERE id = ?", id);
        }
    }

    private Map<String, Object> estructura(Long edicion) {
        return jdbc.queryForMap("""
                SELECT (SELECT count(*) FROM categoria WHERE id_edicion = ?) AS categorias,
                       (SELECT count(*) FROM categoria_opcion o JOIN categoria c ON c.id = o.id_categoria WHERE c.id_edicion = ?) AS opciones,
                       (SELECT count(*) FROM puesto p JOIN categoria c ON c.id = p.id_categoria WHERE c.id_edicion = ?) AS puestos,
                       (SELECT count(*) FROM puesto p JOIN categoria c ON c.id = p.id_categoria
                         WHERE c.id_edicion = ? AND p.estado_puesto = 'L' AND p.mapa_rotacion = 15 AND p.mapa_escala = 1.2) AS libres_iguales,
                       (SELECT count(*) FROM plano_texto WHERE id_edicion = ?) AS textos,
                       (SELECT count(*) FROM entrada_tipo WHERE id_edicion = ?) AS entradas,
                       (SELECT count(*) FROM edicion_dia WHERE id_edicion = ?) AS dias,
                       (SELECT publica FROM edicion WHERE id = ?) AS publica
                """, edicion, edicion, edicion, edicion, edicion, edicion, edicion, edicion);
    }

    @Test
    void duplicarCopiaTodoComoBorradorConEspaciosLibres() {
        ActividadRespaldoService.Resultado r = respaldos.duplicar(original);
        assertThat(r.ok()).isTrue();
        ediciones.add(r.id());
        Map<String, Object> copia = estructura(r.id());
        assertThat(copia).containsEntry("categorias", 1L).containsEntry("opciones", 1L).containsEntry("puestos", 3L)
                .containsEntry("libres_iguales", 3L).containsEntry("textos", 1L).containsEntry("entradas", 1L)
                .containsEntry("dias", 1L).containsEntry("publica", false);
        assertThat(jdbc.queryForObject("SELECT nombre FROM edicion WHERE id = ?", String.class, r.id())).isEqualTo(marca + " (copia)");
    }

    @Test
    void eliminarDejaUnRespaldoQueSePuedeRestaurar() {
        jdbc.update("UPDATE puesto SET estado_puesto = 'L' WHERE id_categoria = ?", categoria);
        ActividadService.Resultado r = actividades.eliminar(original, null);
        assertThat(r.ok()).as(r.mensaje()).isTrue();
        assertThat(r.mensaje()).contains("papelera");
        Map<String, Object> enPapelera = respaldos.papelera().stream()
                .filter(x -> marca.equals(x.get("nombre"))).findFirst().orElseThrow();
        assertThat(enPapelera.get("diasRestantes")).isEqualTo(7);

        ActividadRespaldoService.Resultado restaurada = respaldos.restaurar(((Number) enPapelera.get("id")).longValue());
        assertThat(restaurada.ok()).isTrue();
        ediciones.add(restaurada.id());
        assertThat(estructura(restaurada.id())).containsEntry("puestos", 3L).containsEntry("textos", 1L).containsEntry("opciones", 1L);
        assertThat(jdbc.queryForObject("SELECT nombre FROM edicion WHERE id = ?", String.class, restaurada.id())).isEqualTo(marca);
        assertThat(respaldos.papelera()).noneMatch(x -> marca.equals(x.get("nombre")));
        assertThat(respaldos.restaurar(((Number) enPapelera.get("id")).longValue()).ok()).isFalse();
    }

    @Test
    void unaSolicitudPendienteImpideEliminar() {
        jdbc.update("""
                INSERT INTO interesado_stand (nombre_completo, celular, empresa, rubro, id_categoria, estado)
                VALUES ('Cliente prueba', '70000000', '-', '-', ?, 'NUEVO')
                """, categoria);
        ActividadService.Resultado r = actividades.eliminar(original, null);
        assertThat(r.ok()).isFalse();
        assertThat(r.mensaje()).contains("cancélala o posponla");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM edicion WHERE id = ?", Integer.class, original)).isEqualTo(1);
    }

    @Test
    void cancelarCierraLaVentaYReactivarLaVuelveAAbrir() {
        assertThat(configuracion.cambiarEstado(original, new CambioEstado("POSPUESTA", "Lluvia", null, true)).ok()).isFalse();
        assertThat(configuracion.cambiarEstado(original, new CambioEstado("POSPUESTA", "Lluvia", "20 de diciembre", true)).ok()).isTrue();
        assertThat(jdbc.queryForObject("SELECT finalizada FROM edicion WHERE id = ?", Boolean.class, original)).isFalse();

        assertThat(configuracion.cambiarEstado(original, new CambioEstado("CANCELADA", "Fuerza mayor", null, false)).ok()).isTrue();
        Map<String, Object> f = jdbc.queryForMap("SELECT finalizada, publica, estado_evento FROM edicion WHERE id = ?", original);
        assertThat(f).containsEntry("finalizada", true).containsEntry("publica", false).containsEntry("estado_evento", "CANCELADA");

        assertThat(configuracion.cambiarEstado(original, new CambioEstado("NORMAL", null, null, null)).ok()).isTrue();
        assertThat(jdbc.queryForObject("SELECT finalizada FROM edicion WHERE id = ?", Boolean.class, original)).isFalse();
    }

    @Test
    void antesDeLaAperturaNoSeVendeEnLinea() {
        String sql = "SELECT count(*) FROM categoria WHERE id = ? AND id IN " + Edicion.SQL_CATEGORIAS_VENTA_PUBLICA;
        assertThat(jdbc.queryForObject(sql, Integer.class, categoria)).isEqualTo(1);
        jdbc.update("UPDATE edicion SET venta_desde = now() + interval '1 day' WHERE id = ?", original);
        assertThat(jdbc.queryForObject(sql, Integer.class, categoria)).isZero();
        jdbc.update("UPDATE edicion SET venta_desde = now() - interval '1 minute' WHERE id = ?", original);
        assertThat(jdbc.queryForObject(sql, Integer.class, categoria)).isEqualTo(1);
    }
}

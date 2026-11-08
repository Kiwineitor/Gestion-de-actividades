package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.usic.uniFex.model.service.CredencialDatosService;
import com.usic.uniFex.model.service.PlantillaCredencialService;
import com.usic.uniFex.model.service.PuntoVentaService;
import com.usic.uniFex.model.service.WhatsAppService;

@SpringBootTest
@ActiveProfiles("dev")
class CredencialesPorRolTest {

    @Autowired private PuntoVentaService puntoVenta;
    @Autowired private PlantillaCredencialService plantillas;
    @Autowired private CredencialDatosService datos;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private WhatsAppService whatsApp;

    private Long edicion;
    private Long categoria;
    private Long mesa;
    private Long usuario;

    @BeforeEach
    void preparar() {
        edicion = jdbc.queryForObject("""
                INSERT INTO edicion (nombre, anio, activa, plano_version, plano_sin_fondo, publica, publicacion_independiente,
                                     recibe_interesados, vende_espacios, vende_entradas, comprador, tipo_espacio,
                                     usa_credenciales, credencial_representante, credencial_acompanantes, credencial_entrada)
                VALUES (?, 2026, FALSE, 0, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE, 'PERSONA', 'MESA', TRUE, TRUE, TRUE, TRUE) RETURNING id
                """, Long.class, "Prueba credenciales " + UUID.randomUUID());
        categoria = jdbc.queryForObject("""
                INSERT INTO categoria (nombre, color, forma, precio_base, id_edicion, personas_por_puesto, "_estado", "_fecha_registro")
                VALUES ('Mesa VIP', '#7c3aed', 'circulo', 500, ?, 4, 'A', now()) RETURNING id
                """, Long.class, edicion);
        mesa = jdbc.queryForObject("""
                INSERT INTO puesto (id_categoria, codigo, tamano, estado_puesto, version, mapa_x, mapa_y, precio, "_estado", "_fecha_registro")
                VALUES (?, '4', '1x1', 'L', 0, 0.5, 0.5, 500, 'A', now()) RETURNING id
                """, Long.class, categoria);
        usuario = jdbc.queryForObject("SELECT min(id) FROM usuario", Long.class);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM plantilla_credencial WHERE id_edicion = ?", edicion);
        plantillas.recargar();
        jdbc.update("DELETE FROM auditoria WHERE tabla = 'inscripcion' AND id_registro IN (SELECT id FROM inscripcion WHERE id_edicion = ?)", edicion);
        jdbc.update("DELETE FROM inscripcion_puesto WHERE id_inscripcion IN (SELECT id FROM inscripcion WHERE id_edicion = ?)", edicion);
        List<Long> entidades = jdbc.queryForList("SELECT id_entidad FROM inscripcion WHERE id_edicion = ?", Long.class, edicion);
        jdbc.update("DELETE FROM inscripcion WHERE id_edicion = ?", edicion);
        for (Long e : entidades) {
            List<Long> personas = jdbc.queryForList("SELECT id_persona FROM responsable WHERE id_entidad = ?", Long.class, e);
            jdbc.update("DELETE FROM credencial_cambio WHERE id_responsable IN (SELECT id FROM responsable WHERE id_entidad = ?)", e);
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
    void completaLosAcompanantesSinDatosYUsaElDisenoDeCadaRol() throws Exception {
        var r = puntoVenta.venderEspacios(new PuntoVentaService.VentaEspacios("Juan Perez", "4445556", "71110000", null, null, null,
                null, "EFECTIVO", List.of(new PuntoVentaService.Persona("Ana Perez", "5556667", null)), List.of(mesa)), null, usuario);
        assertThat(r.ok()).as(r.mensaje()).isTrue();
        Long inscripcion = ((Number) r.datos().get("inscripcionId")).longValue();
        List<Map<String, Object>> responsables = jdbc.queryForList("""
                SELECT r.id, r.sin_datos, trim(concat_ws(' ', p.nombre, p.paterno)) AS nombre
                  FROM responsable r JOIN persona p ON p.id = r.id_persona
                 WHERE r.id_entidad = (SELECT id_entidad FROM inscripcion WHERE id = ?) ORDER BY r.id
                """, inscripcion);
        assertThat(responsables).hasSize(4);
        assertThat(responsables.stream().filter(x -> Boolean.TRUE.equals(x.get("sin_datos"))).count()).isEqualTo(2);
        assertThat(responsables.get(2).get("nombre")).isEqualTo("ACOMPAÑANTE 3 DE JUAN PEREZ");

        var acompanante = plantillas.asegurarParaRol(edicion, PlantillaCredencialService.ROL_ACOMPANANTE, usuario);
        assertThat(plantillas.asegurarParaRol(edicion, PlantillaCredencialService.ROL_ACOMPANANTE, usuario).id()).isEqualTo(acompanante.id());
        var representante = plantillas.asegurarParaRol(edicion, PlantillaCredencialService.ROL_REPRESENTANTE, usuario);
        var entrada = plantillas.asegurarParaRol(edicion, PlantillaCredencialService.ROL_ENTRADA, usuario);
        assertThat(acompanante.rol()).isEqualTo("ACOMPANANTE");
        assertThat(acompanante.plantilla().requiereFoto()).isFalse();

        Long sinDatos = ((Number) responsables.get(2).get("id")).longValue();
        Long titular = ((Number) responsables.get(0).get("id")).longValue();
        var porDefecto = plantillas.paraInscripcion(inscripcion);
        assertThat(porDefecto.id()).isEqualTo(representante.plantilla().id());
        assertThat(plantillas.paraResponsable(sinDatos, porDefecto).id()).isEqualTo(acompanante.plantilla().id());
        assertThat(plantillas.paraResponsable(titular, porDefecto).id()).isEqualTo(representante.plantilla().id());
        assertThat(plantillas.paraEntradas(edicion).id()).isEqualTo(entrada.plantilla().id());

        var cambio = datos.actualizar(sinDatos, new CredencialDatosService.Cambio("ROSA", "VACA", "", "7778889", null), usuario);
        assertThat(cambio.ok()).as(cambio.mensaje()).isTrue();
        assertThat(jdbc.queryForObject("SELECT sin_datos FROM responsable WHERE id = ?", Boolean.class, sinDatos)).isFalse();
        assertThat(plantillas.paraResponsable(sinDatos, porDefecto).id()).isEqualTo(representante.plantilla().id());
    }

    @Test
    void sinAcompanantesActivadosNoSeCreanPasesSinDatos() throws Exception {
        jdbc.update("UPDATE edicion SET credencial_acompanantes = FALSE WHERE id = ?", edicion);
        var r = puntoVenta.venderEspacios(new PuntoVentaService.VentaEspacios("Juan Perez", "4445556", "71110000", null, null, null,
                null, "QR", List.of(), List.of(mesa)), null, usuario);
        assertThat(r.ok()).as(r.mensaje()).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM responsable WHERE id_entidad = (SELECT id_entidad FROM inscripcion WHERE id = ?)
                """, Integer.class, ((Number) r.datos().get("inscripcionId")).longValue())).isEqualTo(1);
    }
}

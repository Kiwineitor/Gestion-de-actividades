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

import com.usic.uniFex.model.dto.InteresadoStandRequest;
import com.usic.uniFex.model.service.ApartadoPublicoService;
import com.usic.uniFex.model.service.CancelarInscripcionService;
import com.usic.uniFex.model.service.EntregaVentaService;
import com.usic.uniFex.model.service.InteresadoStandService;
import com.usic.uniFex.model.service.LlegadaService;
import com.usic.uniFex.model.service.ResponsableExtraService;
import com.usic.uniFex.model.service.WhatsAppService;

@SpringBootTest
@ActiveProfiles("dev")
class LlegadasYPersonasTest {

    @Autowired private ApartadoPublicoService apartados;
    @Autowired private InteresadoStandService solicitudes;
    @Autowired private CancelarInscripcionService cancelaciones;
    @Autowired private ResponsableExtraService personas;
    @Autowired private EntregaVentaService entregas;
    @Autowired private LlegadaService llegadas;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private WhatsAppService whatsApp;

    private final String nombre = "Prueba llegadas " + UUID.randomUUID();
    private Long edicion;
    private Long categoria;
    private List<Long> puestos;
    private Long usuario;
    private Long tipo;

    @BeforeEach
    void preparar() {
        edicion = jdbc.queryForObject("""
                INSERT INTO edicion (nombre, anio, activa, plano_version, plano_sin_fondo, publica, publicacion_independiente,
                                     recibe_interesados, minutos_bloqueo, comprador, tipo_espacio, usa_credenciales, credencial_representante)
                VALUES (?, 2026, FALSE, 0, TRUE, TRUE, TRUE, TRUE, 5, 'PERSONA', 'MESA', FALSE, FALSE) RETURNING id
                """, Long.class, nombre);
        categoria = jdbc.queryForObject("""
                INSERT INTO categoria (nombre, color, forma, precio_base, id_edicion, personas_por_puesto, costo_persona_extra, "_estado", "_fecha_registro")
                VALUES ('Mesa general', '#16a34a', 'circulo', 100, ?, 1, 0, 'A', now()) RETURNING id
                """, Long.class, edicion);
        puestos = jdbc.queryForList("""
                INSERT INTO puesto (id_categoria, codigo, tamano, estado_puesto, version, mapa_x, mapa_y, "_estado", "_fecha_registro")
                SELECT ?, n::text, '1x1', 'L', 0, 0.1 * n, 0.5, 'A', now() FROM generate_series(1, 3) n RETURNING id
                """, Long.class, categoria);
        usuario = jdbc.queryForObject("SELECT min(id) FROM usuario", Long.class);
        tipo = jdbc.queryForObject("SELECT id FROM tipo_entidad WHERE nombre = 'PERSONA PARTICULAR'", Long.class);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM llegada WHERE id_inscripcion IN (SELECT id FROM inscripcion WHERE id_edicion = ?)", edicion);
        jdbc.update("DELETE FROM auditoria WHERE tabla = 'inscripcion' AND id_registro IN (SELECT id FROM inscripcion WHERE id_edicion = ?)", edicion);
        jdbc.update("UPDATE interesado_stand SET id_inscripcion = NULL WHERE id_categoria = ?", categoria);
        jdbc.update("DELETE FROM inscripcion_puesto WHERE id_inscripcion IN (SELECT id FROM inscripcion WHERE id_edicion = ?)", edicion);
        List<Long> entidades = jdbc.queryForList("SELECT id_entidad FROM inscripcion WHERE id_edicion = ?", Long.class, edicion);
        jdbc.update("DELETE FROM inscripcion WHERE id_edicion = ?", edicion);
        for (Long e : entidades) {
            List<Long> ps = jdbc.queryForList("SELECT id_persona FROM responsable WHERE id_entidad = ?", Long.class, e);
            jdbc.update("DELETE FROM responsable WHERE id_entidad = ?", e);
            for (Long p : ps) jdbc.update("DELETE FROM persona WHERE id = ?", p);
            jdbc.update("DELETE FROM entidad WHERE id = ?", e);
        }
        jdbc.update("DELETE FROM interesado_stand WHERE id_categoria = ?", categoria);
        jdbc.update("DELETE FROM puesto WHERE id_categoria = ?", categoria);
        jdbc.update("DELETE FROM categoria WHERE id = ?", categoria);
        jdbc.update("DELETE FROM edicion WHERE id = ?", edicion);
    }

    private Long venta(Long puesto, String cliente, String ci) throws Exception {
        var a = apartados.apartar(puesto, "cliente-" + puesto);
        var r = solicitudes.registrar(new InteresadoStandRequest(cliente, "71234567", cliente, "Particular", categoria, puesto, null, ci, a.token()), null);
        assertThat(r.ok()).as(r.mensaje()).isTrue();
        var ap = solicitudes.aprobarComoVenta(r.interesado().getId(), new InteresadoStandService.Aprobacion(null, null, tipo, null, null, true), usuario);
        assertThat(ap.ok()).as(ap.mensaje()).isTrue();
        return ap.interesado().getInscripcionId();
    }

    @Test
    void sinCredencialesNoSeRegistranPersonasNiExtras() throws Exception {
        Long v = venta(puestos.get(0), "JUAN BOLOS", "5550001");
        assertThat(personas.registraPersonas(v)).isFalse();
        assertThat(entregas.deInscripcion(v)).containsEntry("registraPersonas", false);
        var cupo = personas.cupoDe(v);
        assertThat(cupo.registraPersonas()).isFalse();
        assertThat(cupo.admiteExtras()).isFalse();
        var r = personas.agregar(v, new ResponsableExtraService.NuevoResponsable("OTRA", "", "", "999", null, null), null, usuario);
        assertThat(r.ok()).isFalse();
        assertThat(r.mensaje()).contains("no registra personas");
    }

    @Test
    void lasPersonasExtraSoloSiLaCategoriaLasAdmite() throws Exception {
        jdbc.update("UPDATE edicion SET usa_credenciales = TRUE, credencial_representante = TRUE WHERE id = ?", edicion);
        Long v = venta(puestos.get(1), "ANA MESA", "5550002");
        var lleno = personas.cupoDe(v);
        assertThat(lleno.registraPersonas()).isTrue();
        assertThat(lleno.dentroDelDerecho()).isFalse();
        assertThat(lleno.admiteExtras()).isFalse();
        var rechazo = personas.agregar(v, new ResponsableExtraService.NuevoResponsable("EXTRA", "", "", "888", null, null), null, usuario);
        assertThat(rechazo.ok()).isFalse();
        assertThat(rechazo.mensaje()).contains("no admite");

        jdbc.update("UPDATE categoria SET costo_persona_extra = 30 WHERE id = ?", categoria);
        var conPrecio = personas.cupoDe(v);
        assertThat(conPrecio.admiteExtras()).isTrue();
        assertThat(conPrecio.costoSiguiente()).isEqualByComparingTo("30");
        var sinComprobante = personas.agregar(v, new ResponsableExtraService.NuevoResponsable("EXTRA", "", "", "888", null, null), null, usuario);
        assertThat(sinComprobante.ok()).isFalse();
        assertThat(sinComprobante.mensaje()).contains("comprobante");
    }

    @Test
    void laRecepcionConfirmaLaLlegadaUnaVezPorDia() throws Exception {
        Long v = venta(puestos.get(2), "CARLOS LLEGADA", "5550003");
        Map<String, Object> antes = llegadas.listar(edicion, null);
        assertThat(antes).containsEntry("ventas", 1L).containsEntry("llegaron", 0L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> porMesa = (List<Map<String, Object>>) llegadas.listar(edicion, "3").get("lista");
        assertThat(porMesa).extracting(m -> m.get("id")).containsExactly(v);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> porCi = (List<Map<String, Object>>) llegadas.listar(edicion, "5550003").get("lista");
        assertThat(porCi).hasSize(1);
        assertThat(porCi.getFirst()).containsEntry("cobrada", false).containsEntry("conCredenciales", false);

        assertThat(llegadas.confirmar(v, null, null, usuario).ok()).isTrue();
        var repetida = llegadas.confirmar(v, null, null, usuario);
        assertThat(repetida.ok()).isFalse();
        assertThat(repetida.repetido()).isTrue();
        assertThat(repetida.mensaje()).contains("Ya se había confirmado");
        assertThat(llegadas.listar(edicion, null)).containsEntry("llegaron", 1L);

        assertThat(llegadas.deshacer(v).ok()).isTrue();
        assertThat(llegadas.listar(edicion, null)).containsEntry("llegaron", 0L);

        assertThat(cancelaciones.cancelar(v, "Prueba", usuario, "WEB", true).ok()).isTrue();
        assertThat(llegadas.confirmar(v, null, null, usuario).ok()).isFalse();
        assertThat(llegadas.listar(edicion, null)).containsEntry("ventas", 0L);
    }
}

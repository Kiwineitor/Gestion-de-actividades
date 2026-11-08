package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.usic.uniFex.model.dto.InteresadoStandRequest;
import com.usic.uniFex.model.service.AnulacionService;
import com.usic.uniFex.model.service.ApartadoPublicoService;
import com.usic.uniFex.model.service.CancelarInscripcionService;
import com.usic.uniFex.model.service.InteresadoStandService;
import com.usic.uniFex.model.service.WhatsAppService;

@SpringBootTest
@ActiveProfiles("dev")
class AnulacionesTest {

    @Autowired private ApartadoPublicoService apartados;
    @Autowired private InteresadoStandService solicitudes;
    @Autowired private CancelarInscripcionService cancelaciones;
    @Autowired private AnulacionService anulaciones;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private WhatsAppService whatsApp;
    @Value("${app.upload-root}") private String raizArchivos;

    private final String nombre = "Prueba anulaciones " + UUID.randomUUID();
    private final String cliente = "CLIENTE ANULACION " + UUID.randomUUID().toString().replaceAll("[^a-z]", "").toUpperCase();
    private Long edicion;
    private Long categoria;
    private Long otraCategoria;
    private List<Long> puestos;
    private Long usuario;
    private Long tipo;

    @BeforeEach
    void preparar() {
        edicion = jdbc.queryForObject("""
                INSERT INTO edicion (nombre, anio, activa, plano_version, plano_sin_fondo, publica, publicacion_independiente,
                                     recibe_interesados, minutos_bloqueo, comprador, tipo_espacio)
                VALUES (?, 2026, FALSE, 0, TRUE, TRUE, TRUE, TRUE, 5, 'PERSONA', 'MESA') RETURNING id
                """, Long.class, nombre);
        categoria = jdbc.queryForObject("""
                INSERT INTO categoria (nombre, color, forma, precio_base, id_edicion, personas_por_puesto, "_estado", "_fecha_registro")
                VALUES ('Mesa VIP', '#7c3aed', 'circulo', 500, ?, 1, 'A', now()) RETURNING id
                """, Long.class, edicion);
        otraCategoria = jdbc.queryForObject("""
                INSERT INTO categoria (nombre, color, forma, precio_base, id_edicion, personas_por_puesto, "_estado", "_fecha_registro")
                VALUES ('Mesa General', '#16a34a', 'circulo', 200, ?, 1, 'A', now()) RETURNING id
                """, Long.class, edicion);
        puestos = jdbc.queryForList("""
                INSERT INTO puesto (id_categoria, codigo, tamano, estado_puesto, version, mapa_x, mapa_y, "_estado", "_fecha_registro")
                SELECT ?, n::text, '1x1', 'L', 0, 0.1 * n, 0.5, 'A', now() FROM generate_series(1, 4) n RETURNING id
                """, Long.class, categoria);
        usuario = jdbc.queryForObject("SELECT min(id) FROM usuario", Long.class);
        tipo = jdbc.queryForObject("SELECT id FROM tipo_entidad WHERE nombre = 'PERSONA PARTICULAR'", Long.class);
    }

    @AfterEach
    void limpiar() throws Exception {
        for (String archivo : jdbc.queryForList("SELECT comprobante_archivo FROM interesado_stand WHERE id_categoria IN (?, ?) AND comprobante_archivo IS NOT NULL", String.class, categoria, otraCategoria)) {
            Files.deleteIfExists(Path.of(raizArchivos, archivo));
        }
        jdbc.update("DELETE FROM auditoria WHERE tabla = 'inscripcion' AND id_registro IN (SELECT id FROM inscripcion WHERE id_edicion = ?)", edicion);
        jdbc.update("UPDATE interesado_stand SET id_inscripcion = NULL WHERE id_categoria IN (?, ?)", categoria, otraCategoria);
        jdbc.update("DELETE FROM inscripcion_puesto WHERE id_inscripcion IN (SELECT id FROM inscripcion WHERE id_edicion = ?)", edicion);
        List<Long> entidades = jdbc.queryForList("SELECT id_entidad FROM inscripcion WHERE id_edicion = ?", Long.class, edicion);
        jdbc.update("DELETE FROM inscripcion WHERE id_edicion = ?", edicion);
        for (Long e : entidades) {
            List<Long> personas = jdbc.queryForList("SELECT id_persona FROM responsable WHERE id_entidad = ?", Long.class, e);
            jdbc.update("DELETE FROM responsable WHERE id_entidad = ?", e);
            for (Long p : personas) jdbc.update("DELETE FROM persona WHERE id = ?", p);
            jdbc.update("DELETE FROM entidad WHERE id = ?", e);
        }
        jdbc.update("DELETE FROM interesado_stand WHERE id_categoria IN (?, ?)", categoria, otraCategoria);
        jdbc.update("DELETE FROM puesto WHERE id_categoria = ?", categoria);
        jdbc.update("DELETE FROM categoria WHERE id IN (?, ?)", categoria, otraCategoria);
        jdbc.update("DELETE FROM edicion WHERE id = ?", edicion);
    }

    private Long solicitud(Long puesto) throws Exception {
        var a = apartados.apartar(puesto, "cliente-" + puesto);
        var r = solicitudes.registrar(new InteresadoStandRequest(cliente, "71234567", cliente, "Particular", categoria, puesto, null, "1234567", a.token()), null);
        assertThat(r.ok()).as(r.mensaje()).isTrue();
        return r.interesado().getId();
    }

    private Long aprobar(Long solicitudId) throws Exception {
        var r = solicitudes.aprobarComoVenta(solicitudId, new InteresadoStandService.Aprobacion(null, "9999999", tipo, null,
                List.of(new InteresadoStandService.PersonaPuesto("NOMBRE CAMBIADO", "8888888", null)), false), usuario);
        assertThat(r.ok()).as(r.mensaje()).isTrue();
        return r.interesado().getInscripcionId();
    }

    @Test
    void alAprobarNoSePuedenCambiarLosDatosDelCliente() throws Exception {
        Long venta = aprobar(solicitud(puestos.get(0)));
        Map<String, Object> entidad = jdbc.queryForMap("""
                SELECT e.ci_representante, e.representante_legal FROM inscripcion i JOIN entidad e ON e.id = i.id_entidad WHERE i.id = ?
                """, venta);
        assertThat(entidad.get("ci_representante")).isEqualTo("1234567");
        List<String> nombres = jdbc.queryForList("""
                SELECT concat_ws(' ', p.nombre, p.paterno) FROM responsable r JOIN persona p ON p.id = r.id_persona
                  JOIN inscripcion i ON i.id_entidad = r.id_entidad WHERE i.id = ?
                """, String.class, venta);
        assertThat(nombres).noneMatch(n -> n.contains("NOMBRE CAMBIADO"));
    }

    @Test
    void siElPersonalConfirmaElPagoEnPersonaQuedaCobrado() throws Exception {
        var a = apartados.apartar(puestos.get(1), "cliente-oficina");
        var r = solicitudes.registrar(new InteresadoStandRequest(cliente, "71234567", cliente, "Particular", categoria, puestos.get(1), "OFICINA", "1234567", a.token()), null);
        assertThat(r.ok()).as(r.mensaje()).isTrue();
        var ap = solicitudes.aprobarComoVenta(r.interesado().getId(), new InteresadoStandService.Aprobacion(null, null, tipo, null, null, true), usuario);
        assertThat(ap.ok()).as(ap.mensaje()).isTrue();
        assertThat(jdbc.queryForObject("SELECT cobro_efectivo FROM inscripcion WHERE id = ?", Boolean.class, ap.interesado().getInscripcionId())).isTrue();
    }

    @Test
    void rechazarExigeMotivoYQuedaEnAnulaciones() throws Exception {
        Long id = solicitud(puestos.get(1));
        assertThat(solicitudes.cambiarEstado(id, "DESCARTADO", " ", usuario).ok()).isFalse();
        assertThat(solicitudes.cambiarEstado(id, "DESCARTADO", "El comprobante no corresponde", usuario).ok()).isTrue();
        Map<String, Object> fila = anulaciones.listar(edicion, null).stream().filter(a -> "SOLICITUD".equals(a.get("tipo"))).findFirst().orElseThrow();
        assertThat(fila.get("motivo")).isEqualTo("El comprobante no corresponde");
        assertThat(fila.get("anulo")).isNotNull();
        assertThat(fila.get("canal")).isEqualTo("EN_LINEA");

        assertThat(solicitudes.cambiarEstado(id, "NUEVO", null, usuario).ok()).isTrue();
        assertThat(anulaciones.listar(edicion, null)).noneMatch(a -> "SOLICITUD".equals(a.get("tipo")));
    }

    @Test
    void laVentaAnuladaMuestraQuienYPorQueYNoLaVigente() throws Exception {
        Long anulada = aprobar(solicitud(puestos.get(2)));
        Long vigente = aprobar(solicitud(puestos.get(3)));
        assertThat(cancelaciones.cancelar(anulada, "El cliente desistió", usuario, "WEB", true).ok()).isTrue();
        List<Map<String, Object>> lista = anulaciones.listar(edicion, null);
        assertThat(lista).extracting(a -> a.get("id")).contains(anulada).doesNotContain(vigente);
        Map<String, Object> fila = lista.stream().filter(a -> anulada.equals(a.get("id"))).findFirst().orElseThrow();
        assertThat(fila).containsEntry("tipo", "VENTA").containsEntry("motivo", "El cliente desistió").containsEntry("canal", "EN_LINEA");
        assertThat(fila.get("anulo")).isNotNull();
        assertThat(fila.get("etiqueta")).isEqualTo("Venta en línea anulada");
    }

    @Test
    void soloSeCorrigeLaCategoriaYElComprobante() throws Exception {
        Long id = solicitud(puestos.get(0));
        var r = solicitudes.editar(id, new InteresadoStandRequest("OTRO NOMBRE", "70000000", "OTRO NEGOCIO", "Otro", otraCategoria, null, null, null, null));
        assertThat(r.ok()).isTrue();
        Map<String, Object> fila = jdbc.queryForMap("SELECT nombre_completo, celular, id_categoria FROM interesado_stand WHERE id = ?", id);
        assertThat(fila).containsEntry("nombre_completo", cliente).containsEntry("celular", "71234567").containsEntry("id_categoria", otraCategoria);

        var c = solicitudes.reemplazarComprobante(id, new MockMultipartFile("archivo", "voucher.png", "image/png", new byte[] { (byte) 0x89, 'P', 'N', 'G', 1, 2, 3 }), usuario);
        assertThat(c.ok()).as(c.mensaje()).isTrue();
        Map<String, Object> cambio = jdbc.queryForMap("SELECT comprobante_archivo, comprobante_cambiado_por FROM interesado_stand WHERE id = ?", id);
        assertThat((String) cambio.get("comprobante_archivo")).startsWith("comprobantes/");
        assertThat(((Number) cambio.get("comprobante_cambiado_por")).longValue()).isEqualTo(usuario);
    }
}

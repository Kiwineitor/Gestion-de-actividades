package com.usic.uniFex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.service.PuestoReservaService;
import com.usic.uniFex.model.service.RegistroVentaService;
import com.usic.uniFex.model.service.RegistroVentaService.CasetaNoDisponibleException;
import com.usic.uniFex.model.service.RegistroVentaService.DatosPersona;
import com.usic.uniFex.model.service.RegistroVentaService.NuevaVenta;

@SpringBootTest
@ActiveProfiles("dev")
class RegistroVentaTest {

    private static final String NOMBRE_PRUEBA = "ENTIDAD DE PRUEBA AUTOMATICA";
    private static final BigDecimal PRECIO = new BigDecimal("175.00");

    @Autowired private RegistroVentaService registro;
    @Autowired private PuestoReservaService reserva;
    @Autowired private com.usic.uniFex.model.service.ReciboPdfService recibo;
    @Autowired private com.usic.uniFex.model.service.NotaVentaCodigoService codigos;
    @Autowired private IPuestoDao puestoDao;
    @Autowired private JdbcTemplate jdbc;

    private List<Puesto> libres;
    private Long categoriaId;
    private BigDecimal precioOriginal;
    private final java.util.Map<Long, BigDecimal> preciosPropios = new java.util.HashMap<>();
    private final java.util.Map<Long, BigDecimal> preciosOpcion = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        List<Puesto> todas = puestoDao.listarPuestos();
        libres = todas.stream()
                .filter(p -> p.getCategoria() != null)
                .collect(java.util.stream.Collectors.groupingBy(p -> p.getCategoria().getId()))
                .values().stream()
                .filter(g -> g.size() >= 2)
                .findFirst()
                .map(g -> g.subList(0, 2))
                .orElse(List.of());
        Assumptions.assumeTrue(libres.size() == 2,
                "hacen falta 2 casetas libres de la misma categoria para la prueba");

        categoriaId = libres.get(0).getCategoria().getId();
        precioOriginal = jdbc.queryForObject(
                "SELECT precio_base FROM categoria WHERE id=?", BigDecimal.class, categoriaId);
        jdbc.update("UPDATE categoria SET precio_base=? WHERE id=?", PRECIO, categoriaId);

        for (Puesto p : libres) {
            preciosPropios.put(p.getId(), jdbc.queryForObject(
                    "SELECT precio FROM puesto WHERE id=?", BigDecimal.class, p.getId()));
            jdbc.update("UPDATE puesto SET precio=NULL WHERE id=?", p.getId());
        }
        for (var fila : jdbc.queryForList("SELECT id, precio FROM categoria_opcion "
                + "WHERE id_categoria=? AND predeterminada AND _estado <> 'X'", categoriaId)) {
            Long idOpcion = ((Number) fila.get("id")).longValue();
            preciosOpcion.put(idOpcion, (BigDecimal) fila.get("precio"));
            jdbc.update("UPDATE categoria_opcion SET precio=? WHERE id=?", PRECIO, idOpcion);
        }

        limpiarDatosDePrueba();
    }

    @AfterEach
    void tearDown() {
        limpiarDatosDePrueba();
        for (Puesto p : libres) {
            jdbc.update("UPDATE puesto SET estado_puesto='L', reservado_por_id_usuario=NULL, "
                    + "reserva_expira=NULL WHERE id=?", p.getId());
        }
        jdbc.update("UPDATE categoria SET precio_base=? WHERE id=?", precioOriginal, categoriaId);
        preciosPropios.forEach((id, precio) -> jdbc.update("UPDATE puesto SET precio=? WHERE id=?",
                new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.NUMERIC, precio), id));
        preciosOpcion.forEach((id, precio) -> jdbc.update("UPDATE categoria_opcion SET precio=? WHERE id=?",
                new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.NUMERIC, precio), id));
    }

    private void limpiarDatosDePrueba() {
        jdbc.update("DELETE FROM auditoria WHERE tabla='inscripcion' AND id_registro IN "
                + "(SELECT i.id FROM inscripcion i JOIN entidad e ON e.id=i.id_entidad WHERE e.nombre=?)",
                NOMBRE_PRUEBA);
        jdbc.update("DELETE FROM inscripcion_puesto WHERE id_inscripcion IN "
                + "(SELECT i.id FROM inscripcion i JOIN entidad e ON e.id=i.id_entidad WHERE e.nombre=?)",
                NOMBRE_PRUEBA);
        jdbc.update("DELETE FROM inscripcion WHERE id_entidad IN (SELECT id FROM entidad WHERE nombre=?)",
                NOMBRE_PRUEBA);
        List<Long> personas = jdbc.queryForList(
                "SELECT r.id_persona FROM responsable r JOIN entidad e ON e.id=r.id_entidad "
                + "WHERE e.nombre=?", Long.class, NOMBRE_PRUEBA);
        jdbc.update("DELETE FROM responsable WHERE id_entidad IN (SELECT id FROM entidad WHERE nombre=?)",
                NOMBRE_PRUEBA);
        for (Long idPersona : personas) {
            jdbc.update("DELETE FROM persona WHERE id=?", idPersona);
        }
        jdbc.update("DELETE FROM entidad WHERE nombre=?", NOMBRE_PRUEBA);
    }

    private NuevaVenta venta(List<Long> puestos) {
        return new NuevaVenta(
                NOMBRE_PRUEBA, "123456789", "Descripcion", "Objeto",
                "Representante Legal", "99887766", "71234567",
                jdbc.queryForObject("SELECT id FROM tipo_entidad ORDER BY id LIMIT 1", Long.class),
                LocalDate.now(), LocalDate.now().plusDays(5),
                List.of(new DatosPersona("TITULAR", "Perez", "Lopez", "111", "t@x.com", "700"),
                        new DatosPersona("ACOMPANIANTE", "Gomez", "Ruiz", "222", "a@x.com", "701")),
                "Banco X", "555", false,
                puestos);
    }

    @Test
    void registraLaVentaYCongelaElPrecioDeLaCategoria() {
        List<Long> ids = libres.stream().map(Puesto::getId).toList();

        RegistroVentaService.Resultado r = registro.registrar(venta(ids), 1L);

        assertThat(r.ok()).as(r.mensaje()).isTrue();
        assertThat(r.puestosOcupados()).containsExactlyElementsOf(ids);
        assertThat(r.total()).isEqualByComparingTo(PRECIO.multiply(new BigDecimal(ids.size())));

        for (Long id : ids) {
            assertThat(puestoDao.findById(id).orElseThrow().getEstadoPuesto())
                    .isEqualTo(Puesto.OCUPADO);
        }

        List<BigDecimal> costos = jdbc.queryForList(
                "SELECT costo FROM inscripcion_puesto WHERE id_inscripcion=?",
                BigDecimal.class, r.inscripcionId());
        assertThat(costos).allSatisfy(c -> assertThat(c).isEqualByComparingTo(PRECIO));

        Long edicion = jdbc.queryForObject(
                "SELECT id_edicion FROM inscripcion WHERE id=?", Long.class, r.inscripcionId());
        Long activa = jdbc.queryForObject(
                "SELECT id FROM edicion WHERE activa LIMIT 1", Long.class);
        assertThat(edicion).isEqualTo(activa);

        Integer titulares = jdbc.queryForObject(
                "SELECT count(*) FROM responsable r JOIN entidad e ON e.id=r.id_entidad "
                + "WHERE e.nombre=? AND r.es_titular", Integer.class, NOMBRE_PRUEBA);
        assertThat(titulares).isEqualTo(1);
    }

    @Test
    void siUnaCasetaSePierdeNoQuedaNadaAMedias() {
        Long mia = libres.get(0).getId();
        Long ajena = libres.get(1).getId();

        assertThat(reserva.agregarAlCarrito(List.of(ajena), 9999L).todoOk()).isTrue();

        assertThatThrownBy(() -> registro.registrar(venta(List.of(mia, ajena)), 1L))
                .isInstanceOf(CasetaNoDisponibleException.class)
                .hasMessageContaining("ya no esta disponible");

        Integer entidades = jdbc.queryForObject(
                "SELECT count(*) FROM entidad WHERE nombre=?", Integer.class, NOMBRE_PRUEBA);
        assertThat(entidades).as("no debe quedar la entidad de una venta fallida").isZero();

        Integer personas = jdbc.queryForObject(
                "SELECT count(*) FROM persona WHERE ci IN ('111','222')", Integer.class);
        assertThat(personas).as("no deben quedar las personas de una venta fallida").isZero();

        assertThat(puestoDao.findById(mia).orElseThrow().getEstadoPuesto())
                .isNotEqualTo(Puesto.OCUPADO);
    }

    @Test
    void elComprobanteSoloLoAdjuntaQuienRegistroLaVenta() {
        final long vendedor = 1L;
        final long intruso = 4242L;
        RegistroVentaService.Resultado venta =
                registro.registrar(venta(libres.stream().map(Puesto::getId).toList()), vendedor);
        assertThat(venta.ok()).as(venta.mensaje()).isTrue();

        var archivo = new org.springframework.mock.web.MockMultipartFile(
                "archivo", "comprobante.png", "image/png", new byte[] { 1, 2, 3 });

        RegistroVentaService.Resultado ajeno =
                registro.adjuntarComprobante(venta.inscripcionId(), archivo, "Banco Y", "9", intruso);
        assertThat(ajeno.ok()).isFalse();
        assertThat(ajeno.mensaje()).contains("no es tuya");

        assertThat(registro.pendientesDe(vendedor))
                .as("antes de adjuntar, la venta figura como pendiente")
                .anyMatch(p -> p.id().equals(venta.inscripcionId()));

        RegistroVentaService.Resultado ok =
                registro.adjuntarComprobante(venta.inscripcionId(), archivo, "Banco Y", "9", vendedor);
        assertThat(ok.ok()).as(ok.mensaje()).isTrue();

        assertThat(registro.pendientesDe(vendedor))
                .as("con comprobante ya no es pendiente")
                .noneMatch(p -> p.id().equals(venta.inscripcionId()));

        String ruta = jdbc.queryForObject(
                "SELECT img_comprobante FROM inscripcion WHERE id=?", String.class, venta.inscripcionId());
        assertThat(ruta).startsWith("comprobantes/");
    }

    @Test
    void rechazaUnComprobanteConExtensionNoPermitida() {
        RegistroVentaService.Resultado venta =
                registro.registrar(venta(libres.stream().map(Puesto::getId).toList()), 1L);
        assertThat(venta.ok()).isTrue();

        var ejecutable = new org.springframework.mock.web.MockMultipartFile(
                "archivo", "virus.exe", "application/octet-stream", new byte[] { 1 });

        RegistroVentaService.Resultado r =
                registro.adjuntarComprobante(venta.inscripcionId(), ejecutable, null, null, 1L);

        assertThat(r.ok()).isFalse();
        assertThat(r.mensaje()).contains("no permitido");
    }

    @Test
    void generaElReciboAunqueElVendedorNoTengaFichaAdministrativa() throws Exception {
        RegistroVentaService.Resultado venta =
                registro.registrar(venta(libres.stream().map(Puesto::getId).toList()), 1L);
        assertThat(venta.ok()).as(venta.mensaje()).isTrue();

        Integer fichas = jdbc.queryForObject(
                "SELECT count(*) FROM admistrativo a JOIN usuario u ON u.persona_id = a.id_persona "
                + "WHERE u.id = 1", Integer.class);
        assertThat(fichas).as("el escenario que se quiere cubrir es SIN ficha").isZero();

        var salida = new java.io.ByteArrayOutputStream();
        recibo.generarRecibo(venta.inscripcionId(), salida);

        byte[] pdf = salida.toByteArray();
        assertThat(pdf.length).isGreaterThan(1000);
        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1))
                .isEqualTo("%PDF");
    }

    @Test
    void elCodigoDeLaNotaNoCambiaAlReimprimir() throws Exception {
        RegistroVentaService.Resultado venta =
                registro.registrar(venta(libres.stream().map(Puesto::getId).toList()), 1L);
        assertThat(venta.ok()).as(venta.mensaje()).isTrue();

        recibo.generarRecibo(venta.inscripcionId(), new java.io.ByteArrayOutputStream());
        String primero = jdbc.queryForObject(
                "SELECT nota_codigo FROM inscripcion WHERE id=?", String.class, venta.inscripcionId());
        java.sql.Timestamp emitida = jdbc.queryForObject(
                "SELECT nota_emitida_en FROM inscripcion WHERE id=?", java.sql.Timestamp.class,
                venta.inscripcionId());

        assertThat(primero).as("la primera impresion debe emitir el codigo").isNotBlank();
        assertThat(emitida).isNotNull();

        recibo.generarRecibo(venta.inscripcionId(), new java.io.ByteArrayOutputStream());
        assertThat(jdbc.queryForObject(
                "SELECT nota_codigo FROM inscripcion WHERE id=?", String.class, venta.inscripcionId()))
                .isEqualTo(primero);
        assertThat(jdbc.queryForObject(
                "SELECT nota_emitida_en FROM inscripcion WHERE id=?", java.sql.Timestamp.class,
                venta.inscripcionId()))
                .isEqualTo(emitida);
    }

    @Test
    void elCodigoDeLaNotaSePuedeVerificar() throws Exception {
        RegistroVentaService.Resultado venta =
                registro.registrar(venta(libres.stream().map(Puesto::getId).toList()), 1L);
        assertThat(venta.ok()).as(venta.mensaje()).isTrue();
        recibo.generarRecibo(venta.inscripcionId(), new java.io.ByteArrayOutputStream());

        String codigo = jdbc.queryForObject(
                "SELECT nota_codigo FROM inscripcion WHERE id=?", String.class, venta.inscripcionId());

        var ok = codigos.verificar(codigo);
        assertThat(ok.existe()).isTrue();
        assertThat(ok.vigente()).isTrue();
        assertThat(ok.inscripcionId()).isEqualTo(venta.inscripcionId());
        assertThat(ok.entidad()).isEqualTo(NOMBRE_PRUEBA);

        assertThat(codigos.verificar("FEX-" + venta.inscripcionId() + "-000000000000").existe()).isFalse();
        assertThat(codigos.verificar("cualquier cosa").existe()).isFalse();
    }

    @Test
    void rechazaMasResponsablesDeLosQueDanLasCasetas() {
        NuevaVenta base = venta(libres.stream().map(Puesto::getId).toList());
        int derecho = RegistroVentaService.maxResponsables(base.puestos().size());
        List<DatosPersona> deMas = java.util.stream.IntStream.rangeClosed(1, derecho + 1)
                .mapToObj(i -> new DatosPersona("R" + i, "A", "A", String.valueOf(i), null, null))
                .toList();
        NuevaVenta conDeMas = new NuevaVenta(
                base.entidadNombre(), base.nit(), base.descripcion(), base.objeto(),
                base.representanteLegal(), base.ciRepresentante(), base.celularRepresentante(),
                base.tipoEntidadId(),
                base.fechaInicio(), base.fechaFin(),
                deMas,
                base.entidadBancaria(), base.numComprobante(), base.pagoContado(),
                base.puestos());

        RegistroVentaService.Resultado r = registro.registrar(conDeMas, 1L);

        assertThat(r.ok()).isFalse();
        assertThat(r.mensaje()).contains("derecho a " + derecho + " responsables");
        for (Long id : base.puestos()) {
            assertThat(puestoDao.findById(id).orElseThrow().getEstadoPuesto())
                    .isEqualTo(Puesto.LIBRE);
        }
    }
}

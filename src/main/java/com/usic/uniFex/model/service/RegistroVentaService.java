package com.usic.uniFex.model.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.IService.IEntidadService;
import com.usic.uniFex.model.IService.IInscripcionPuestoService;
import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.IService.IPuestoService;
import com.usic.uniFex.model.IService.IResponsableService;
import com.usic.uniFex.model.IService.ITipoEntidadService;
import com.usic.uniFex.model.dao.IEdicionDao;
import com.usic.uniFex.model.dao.IInscripcionDao;
import com.usic.uniFex.model.entity.CategoriaOpcion;
import com.usic.uniFex.model.entity.Edicion;
import com.usic.uniFex.model.entity.Entidad;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.InscripcionPuesto;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.entity.Responsable;
import com.usic.uniFex.model.entity.TipoEntidad;
import com.usic.uniFex.model.dto.PlantillaCredencial;
import com.usic.uniFex.model.repository.FuncionesInscripcion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistroVentaService {

    public static final int RESPONSABLES_POR_CASETA = 2;

    public static final java.math.BigDecimal COSTO_RESPONSABLE_EXTRA = new java.math.BigDecimal("15");

    public static int maxResponsables(int casetas) {
        return Math.max(1, casetas) * RESPONSABLES_POR_CASETA;
    }

    private static final String RESPONSABLE = "RESPONSABLE";
    private static final String ACTIVO = "ACTIVO";
    private static final String PENDIENTE = "PENDIENTE";

    private final IEntidadService entidadService;

    private final CategoriaOpcionService opcionService;
    private final IPersonaService personaService;
    private final IResponsableService responsableService;
    private final IInscripcionService inscripcionService;
    private final IInscripcionPuestoService inscripcionPuestoService;
    private final IPuestoService puestoService;
    private final ITipoEntidadService tipoEntidadService;
    private final IEdicionDao edicionDao;
    private final IInscripcionDao inscripcionDao;
    private final FuncionesInscripcion funciones;
    private final PuestoReservaService reservaService;
    private final PuestoEventPublisher publisher;
    private final FileStorageService storage;
    private final AuditoriaService auditoria;
    private final WhatsAppService whatsApp;
    private final ReciboPdfService reciboPdf;
    private final CredencialImagenService credencialImagen;
    private final CredencialService credencialService;
    @Value("${app.base-url:http://localhost:7676}")
    private String baseUrl;

    public record DatosPersona(String nombre, String paterno, String materno, String ci,
                               String correo, String celular) {
    }

    public record NuevaVenta(
            String entidadNombre, String nit, String descripcion, String objeto,
            String representanteLegal, String ciRepresentante, String celularRepresentante,
            Long tipoEntidadId,
            LocalDate fechaInicio, LocalDate fechaFin,
            List<DatosPersona> responsables,
            String entidadBancaria, String numComprobante, Boolean pagoContado,
            List<Long> puestos,
            Map<Long, Long> opcionesPorCategoria) {

        public NuevaVenta(String entidadNombre, String nit, String descripcion, String objeto,
                          String representanteLegal, String ciRepresentante, String celularRepresentante,
                          Long tipoEntidadId, LocalDate fechaInicio, LocalDate fechaFin,
                          List<DatosPersona> responsables, String entidadBancaria,
                          String numComprobante, Boolean pagoContado, List<Long> puestos) {
            this(entidadNombre, nit, descripcion, objeto, representanteLegal, ciRepresentante,
                 celularRepresentante, tipoEntidadId, fechaInicio, fechaFin, responsables,
                 entidadBancaria, numComprobante, pagoContado, puestos, null);
        }
    }

    public record Resultado(boolean ok, String mensaje, Long inscripcionId,
                            List<Long> puestosOcupados, BigDecimal total) {
        static Resultado error(String mensaje) {
            return new Resultado(false, mensaje, null, List.of(), BigDecimal.ZERO);
        }
    }

    public static class CasetaNoDisponibleException extends RuntimeException {
        public CasetaNoDisponibleException(String mensaje) {
            super(mensaje);
        }
    }

    public record VentaPendiente(Long id, String entidad, LocalDateTime fechaCompra,
                                 String entidadBancaria, String numComprobante,
                                 long diasSinComprobante, BigDecimal total) {
    }

    @Transactional
    public Resultado adjuntarComprobante(Long inscripcionId, MultipartFile archivo,
                                         String entidadBancaria, String numComprobante,
                                         Long usuarioId) {
        return adjuntarComprobante(inscripcionId, archivo, entidadBancaria, numComprobante,
                usuarioId, AuditoriaService.ORIGEN_WEB);
    }

    @Transactional
    public Resultado adjuntarComprobante(Long inscripcionId, MultipartFile archivo,
                                         String entidadBancaria, String numComprobante,
                                         Long usuarioId, String origen) {
        if (numComprobante != null && numComprobante.length() > Inscripcion.MAX_NUM_COMPROBANTE) {
            return Resultado.error("El numero de comprobante admite hasta 100 caracteres");
        }
        Inscripcion i = inscripcionService.findById(inscripcionId);
        if (i == null) return Resultado.error("La venta no existe");
        if (!usuarioId.equals(i.getRegistroIdUsuario()) && !puedeAcreditar()) {
            return Resultado.error("Esta venta no es tuya");
        }
        if (archivo == null || archivo.isEmpty()) {
            return Resultado.error("No se recibio ningun archivo");
        }

        try {
            String nombre = i.getEntidad() != null ? i.getEntidad().getNombre() : ("venta-" + inscripcionId);
            String ruta = storage.save(archivo, FileStorageService.Bucket.COMPROBANTES, nombre);
            i.setImgComprobante(ruta);
            if (entidadBancaria != null && !entidadBancaria.isBlank()) i.setEntidadBancaria(entidadBancaria);
            if (numComprobante != null) i.setNumComprobante(numComprobante);
            i.setModificacion(new Date());
            i.setModificacionIdUsuario(usuarioId);
            inscripcionService.save(i);
            auditoria.registrar(AuditoriaService.TABLA_INSCRIPCION, inscripcionId,
                    AuditoriaService.ACCION_COMPROBANTE,
                    "Comprobante Nº " + (numComprobante != null ? numComprobante : "—")
                            + (entidadBancaria != null && !entidadBancaria.isBlank()
                                    ? " · " + entidadBancaria : ""),
                    usuarioId, origen);
            log.info("Comprobante adjuntado a inscripcion={} por usuario={}", inscripcionId, usuarioId);
            return new Resultado(true, "Comprobante adjuntado", inscripcionId, List.of(), BigDecimal.ZERO);
        } catch (IOException e) {
            log.warn("No se pudo guardar el comprobante de la inscripcion {}: {}", inscripcionId, e.getMessage());
            return Resultado.error("No se pudo guardar el archivo: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<VentaPendiente> pendientesDe(Long usuarioId) {
        return inscripcionDao.pendientesDeComprobante(usuarioId).stream()
                .map(i -> new VentaPendiente(
                        i.getId(),
                        i.getEntidad() != null ? i.getEntidad().getNombre() : "(sin entidad)",
                        i.getFechaCompra(),
                        i.getEntidadBancaria(),
                        i.getNumComprobante(),
                        i.getFechaCompra() == null ? 0
                                : ChronoUnit.DAYS.between(i.getFechaCompra().toLocalDate(), LocalDate.now()),
                        i.getInscripcionPuestos().stream()
                                .map(ip -> ip.getCosto() != null ? ip.getCosto() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();
    }

    @Transactional
    public Resultado registrar(NuevaVenta req, Long usuarioId) {
        return registrar(req, usuarioId, AuditoriaService.ORIGEN_WEB);
    }

    @Transactional
    public Resultado registrar(NuevaVenta req, Long usuarioId, String origen) {
        String error = validar(req);
        if (error != null) return Resultado.error(error);

        TipoEntidad tipo = tipoEntidadService.findById(req.tipoEntidadId());
        if (tipo == null) return Resultado.error("El tipo de entidad no existe");

        Date ahora = new Date();

        Entidad entidad = crearEntidad(req, tipo, ahora, usuarioId);
        crearResponsables(req.responsables(), entidad, ahora, usuarioId);
        Inscripcion inscripcion = crearInscripcion(req, entidad, ahora, usuarioId);

        List<Long> ocupados = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Long puestoId : req.puestos()) {
            if (!reservaService.ocupar(puestoId, usuarioId)) {
                Puesto p = puestoService.findById(puestoId);
                String cual = (p != null)
                        ? (p.getCategoria() != null ? p.getCategoria().getNombre() + " " : "") + p.getCodigo()
                        : ("id " + puestoId);
                throw new CasetaNoDisponibleException(
                        "La caseta " + cual + " ya no esta disponible. Revisa el mapa y vuelve a intentarlo.");
            }
            ocupados.add(puestoId);
            total = total.add(guardarDetalle(puestoId, inscripcion, tipo, ahora, usuarioId,
                                             req.opcionesPorCategoria()));
        }

        auditoria.registrar(AuditoriaService.TABLA_INSCRIPCION, inscripcion.getId(),
                AuditoriaService.ACCION_REGISTRO,
                "Venta registrada: " + (entidad.getNombre() != null ? entidad.getNombre() : "(sin entidad)")
                        + " · " + ocupados.size() + " caseta(s) · total " + total + " Bs",
                usuarioId, origen);

        difundirTrasCommit(ocupados);
        log.info("Venta registrada inscripcion={} casetas={} total={} usuario={}",
                inscripcion.getId(), ocupados.size(), total, usuarioId);

        return new Resultado(true, "Venta registrada", inscripcion.getId(), ocupados, total);
    }

    private static boolean puedeAcreditar() {
        org.springframework.security.core.Authentication a =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
        if (a == null) return false;
        return a.getAuthorities().stream().map(g -> g.getAuthority()).anyMatch(
                r -> "ROLE_SUPER_USUARIO".equals(r) || "ROLE_ADMINISTRADOR".equals(r)
                        || "ROLE_VERIFICADOR".equals(r));
    }

    private String validar(NuevaVenta req) {
        if (req == null) return "Faltan los datos de la venta";
        if (req.numComprobante() != null && req.numComprobante().length() > Inscripcion.MAX_NUM_COMPROBANTE) {
            return "El numero de comprobante admite hasta 100 caracteres";
        }
        if (vacio(req.entidadNombre())) return "El nombre de la entidad es obligatorio";
        if (req.tipoEntidadId() == null) return "Falta el tipo de entidad";
        if (vacio(req.representanteLegal())) return "Falta el nombre del responsable legal";
        if (vacio(req.ciRepresentante())) return "Falta el C.I. del responsable legal";
        if (vacio(req.celularRepresentante())) return "Falta el celular del responsable legal";
        if (req.puestos() == null || req.puestos().isEmpty()) return "No se selecciono ninguna caseta";
        if (req.puestos().stream().distinct().count() != req.puestos().size()) {
            return "Hay casetas repetidas en la seleccion";
        }
        if (req.responsables() == null || req.responsables().isEmpty()) {
            return "Hace falta al menos el Responsable 1";
        }
        int derecho = maxResponsables(req.puestos().size());
        if (req.responsables().size() > derecho) {
            return req.puestos().size() + " caseta(s) dan derecho a " + derecho
                 + " responsables. Los adicionales se agregan desde Mis ventas, con su pago.";
        }
        for (DatosPersona p : req.responsables()) {
            if (vacio(p.nombre())) return "Cada responsable necesita nombre";
            if (vacio(p.ci())) return "Cada responsable necesita C.I.";
        }
        return null;
    }

    private Entidad crearEntidad(NuevaVenta req, TipoEntidad tipo, Date ahora, Long usuarioId) {
        Entidad e = new Entidad();
        e.setNombre(req.entidadNombre().trim());
        e.setNit(req.nit());
        e.setDescripcion(req.descripcion());
        e.setObjeto(req.objeto());
        e.setRepresentanteLegal(req.representanteLegal());
        e.setCiRepresentante(req.ciRepresentante());
        e.setCelularRepresentante(req.celularRepresentante());
        e.setTipoEntidad(tipo);
        e.setEstado(ACTIVO);
        sellar(e, ahora, usuarioId);
        return entidadService.save(e);
    }

    private void crearResponsables(List<DatosPersona> datos, Entidad entidad, Date ahora, Long usuarioId) {
        for (int i = 0; i < datos.size(); i++) {
            DatosPersona d = datos.get(i);

            Persona persona = new Persona();
            persona.setNombre(d.nombre());
            persona.setPaterno(d.paterno());
            persona.setMaterno(d.materno());
            persona.setCi(d.ci());
            persona.setCorreo(d.correo());
            persona.setCelular(d.celular());
            persona.setEstado(RESPONSABLE);
            sellar(persona, ahora, usuarioId);
            personaService.save(persona);

            Responsable r = new Responsable();
            r.setEntidad(entidad);
            r.setPersona(persona);
            r.setEsTitular(i == 0);
            r.setEstado(RESPONSABLE);
            sellar(r, ahora, usuarioId);
            responsableService.save(r);
        }
    }

    private Inscripcion crearInscripcion(NuevaVenta req, Entidad entidad, Date ahora, Long usuarioId) {
        Inscripcion i = new Inscripcion();
        i.setEntidad(entidad);
        i.setFechaCompra(LocalDateTime.now());
        i.setInscripcionEstado(PENDIENTE);
        i.setEstado(ACTIVO);
        if (req.fechaInicio() != null) i.setFechaInicio(java.sql.Date.valueOf(req.fechaInicio()));
        if (req.fechaFin() != null) i.setFechaFin(java.sql.Date.valueOf(req.fechaFin()));
        i.setEntidadBancaria(req.entidadBancaria());
        i.setNumComprobante(req.numComprobante());
        i.setPagoContado(Boolean.TRUE.equals(req.pagoContado()));

        Edicion edicion = edicionDao.findFirstByActivaTrueOrderByAnioDesc().orElse(null);
        if (edicion == null) log.warn("No hay edicion activa: la inscripcion se registra sin edicion");
        i.setEdicion(edicion);

        sellar(i, ahora, usuarioId);
        return inscripcionService.save(i);
    }

    private BigDecimal guardarDetalle(Long puestoId, Inscripcion inscripcion, TipoEntidad tipo,
                                      Date ahora, Long usuarioId, Map<Long, Long> elegidas) {
        Puesto puesto = puestoService.findById(puestoId);
        Long categoriaId = puesto.getCategoria() != null ? puesto.getCategoria().getId() : null;

        CategoriaOpcion opcion = null;
        if (categoriaId != null) {
            opcion = opcionService.resolverParaVenta(
                    categoriaId, elegidas == null ? null : elegidas.get(categoriaId));
        }

        BigDecimal costo = null;
        if (puesto.getPrecio() != null) {
            costo = puesto.getPrecio();
            opcion = null;
        }
        if (costo == null) costo = opcion != null ? opcion.getPrecio() : null;
        if (costo == null) {
            costo = funciones.obtenerCostoPuesto(tipo.getId(), puesto.getTamano(), categoriaId);
        }
        if (costo == null) costo = BigDecimal.ZERO;

        InscripcionPuesto ip = new InscripcionPuesto();
        ip.setPuesto(puesto);
        ip.setInscripcion(inscripcion);
        ip.setCosto(costo);
        ip.setOpcion(opcion);
        ip.setEstado(ACTIVO);
        sellar(ip, ahora, usuarioId);
        inscripcionPuestoService.save(ip);
        return costo;
    }

    private void difundirTrasCommit(List<Long> ocupados) {
        if (ocupados.isEmpty()) return;
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publisher.publicarVarios(ocupados);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.publicarVarios(ocupados);
            }
        });
    }

    private void enviarWhatsAppTrasCommit(String celular, String nombreEntidad, Long inscripcionId) {
        if (!whatsApp.habilitado()) {
            log.debug("WhatsApp deshabilitado; no se generan PDFs para inscripcion {}", inscripcionId);
            return;
        }
        if (celular == null || celular.isBlank()) {
            log.debug("Sin celular de responsable legal; no se envía WhatsApp para inscripción {}", inscripcionId);
            return;
        }
        String normalizado = celular.replaceAll("[^0-9]", "");
        if (normalizado.startsWith("0")) normalizado = normalizado.substring(1);
        if (normalizado.length() == 8) normalizado = "591" + normalizado;

        final String numeroFinal = normalizado;
        Runnable tarea = com.usic.uniFex.Config.ContextoRegistro.conservar(
                () -> enviarWhatsAppVentaConPdfs(numeroFinal, nombreEntidad, inscripcionId));
        Runnable envio = () -> CompletableFuture.runAsync(tarea);

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            envio.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                envio.run();
            }
        });
    }

    private void enviarWhatsAppVentaConPdfs(String celular, String nombreEntidad, Long inscripcionId) {
        try {
            log.info("[WHATSAPP-VENTA] Inicio envio automatico inscripcion={} celular={} entidad='{}' baseUrl={}",
                    inscripcionId, celular, nombreEntidad, baseUrl);
            ByteArrayOutputStream salidaRecibo = new ByteArrayOutputStream();
            reciboPdf.generarRecibo(inscripcionId, salidaRecibo);
            log.info("[WHATSAPP-VENTA] Recibo generado inscripcion={} bytes={}",
                    inscripcionId, salidaRecibo.size());

            PlantillaCredencial plantilla = PlantillaCredencial.actual(PlantillaCredencial.CREDENCIAL_VIRTUAL);
            var datosCredenciales = credencialService.porInscripcion(inscripcionId);
            log.info("[WHATSAPP-VENTA] Credenciales encontradas inscripcion={} cantidad={}",
                    inscripcionId, datosCredenciales.size());
            List<byte[]> credenciales = datosCredenciales.stream()
                    .peek(c -> log.info("[WHATSAPP-VENTA] Preparando credencial responsable={} nombre='{}' fotoUrl='{}' conFoto={} conComprobante={} aptaVirtual={}",
                            c.responsableId(), c.nombre(), c.fotoUrl(), c.conFoto(), c.conComprobante(), c.apto(plantilla.id())))
                    .map(c -> credencialImagen.generar(c, plantilla, baseUrl))
                    .toList();
            log.info("[WHATSAPP-VENTA] Credenciales generadas inscripcion={} cantidad={}",
                    inscripcionId, credenciales.size());

            whatsApp.enviarBienvenidaVentaConPdfs(celular, nombreEntidad, inscripcionId,
                    salidaRecibo.toByteArray(), credenciales, baseUrl);
            log.info("[WHATSAPP-VENTA] Fin envio automatico inscripcion={} archivosCredencial={}",
                    inscripcionId, credenciales.size());
        } catch (Exception e) {
            log.error("No se pudieron generar/enviar PDFs por WhatsApp para inscripcion {}",
                    inscripcionId, e);
        }
    }

    private void sellar(com.usic.uniFex.Config.AuditoriaConfig e, Date ahora, Long usuarioId) {
        e.setRegistro(ahora);
        e.setRegistroIdUsuario(usuarioId);
        e.setModificacion(ahora);
        e.setModificacionIdUsuario(usuarioId);
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}

package com.usic.uniFex.model.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.IService.IResponsableService;
import com.usic.uniFex.model.dao.IInscripcionPuestoDao;
import com.usic.uniFex.model.dao.IResponsableDao;
import com.usic.uniFex.model.entity.Entidad;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Responsable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResponsableExtraService {

    private static final String RESPONSABLE = "RESPONSABLE";

    private final IInscripcionService inscripcionService;
    private final IInscripcionPuestoDao inscripcionPuestoDao;
    private final IResponsableDao responsableDao;
    private final IPersonaService personaService;
    private final IResponsableService responsableService;
    private final FileStorageService almacen;

    public record NuevoResponsable(String nombre, String paterno, String materno, String ci,
                                   String correo, String celular) {
    }

    public record Cupo(int casetas, int derecho, int registrados, int extras, boolean dentroDelDerecho,
                       BigDecimal costoSiguiente) {
    }

    public record Resultado(boolean ok, String mensaje, Long responsableId, boolean cobrado,
                            BigDecimal monto) {
        static Resultado error(String m) { return new Resultado(false, m, null, false, null); }
    }

    @Transactional(readOnly = true)
    public Cupo cupoDe(Long inscripcionId) {
        Inscripcion i = inscripcionService.findById(inscripcionId);
        if (i == null) return null;
        int casetas = inscripcionPuestoDao.contarActivosDeInscripcion(inscripcionId);
        int derecho = RegistroVentaService.maxResponsables(casetas);
        List<Responsable> vivos = responsableDao.vivosDeEntidad(i.getEntidad().getId());
        int extras = (int) vivos.stream().filter(Responsable::isEsExtra).count();
        boolean dentro = vivos.size() < derecho;
        return new Cupo(casetas, derecho, vivos.size(), extras, dentro,
                dentro ? BigDecimal.ZERO : RegistroVentaService.COSTO_RESPONSABLE_EXTRA);
    }

    @Transactional
    public Resultado agregar(Long inscripcionId, NuevoResponsable datos, MultipartFile comprobante,
                             Long usuarioId) {
        Inscripcion i = inscripcionService.findById(inscripcionId);
        if (i == null) return Resultado.error("La venta no existe");
        if (datos == null || vacio(datos.nombre())) return Resultado.error("Falta el nombre");
        if (vacio(datos.ci())) return Resultado.error("Falta el C.I.");

        Cupo cupo = cupoDe(inscripcionId);
        boolean cobra = !cupo.dentroDelDerecho();
        if (cobra && (comprobante == null || comprobante.isEmpty())) {
            return Resultado.error("Este responsable pasa de los " + cupo.derecho()
                    + " que dan sus " + cupo.casetas() + " caseta(s): hace falta el comprobante de "
                    + RegistroVentaService.COSTO_RESPONSABLE_EXTRA + " Bs");
        }

        String ruta = null;
        if (comprobante != null && !comprobante.isEmpty()) {
            try {
                ruta = almacen.save(comprobante, FileStorageService.Bucket.COMPROBANTES,
                        i.getEntidad().getNombre() + "-extra");
            } catch (IOException e) {
                log.warn("No se pudo guardar el comprobante del responsable extra: {}", e.getMessage());
                return Resultado.error("No se pudo guardar el comprobante: " + e.getMessage());
            }
        }

        Date ahora = new Date();
        Entidad entidad = i.getEntidad();

        Persona persona = new Persona();
        persona.setNombre(datos.nombre());
        persona.setPaterno(datos.paterno());
        persona.setMaterno(datos.materno());
        persona.setCi(datos.ci());
        persona.setCorreo(datos.correo());
        persona.setCelular(datos.celular());
        persona.setEstado(RESPONSABLE);
        persona.setRegistro(ahora);
        persona.setRegistroIdUsuario(usuarioId);
        personaService.save(persona);

        Responsable r = new Responsable();
        r.setEntidad(entidad);
        r.setPersona(persona);
        r.setEsTitular(false);
        r.setEsExtra(cobra);
        r.setComprobanteExtra(ruta);
        if (cobra) {
            r.setMontoExtra(RegistroVentaService.COSTO_RESPONSABLE_EXTRA);
        }
        r.setEstado(RESPONSABLE);
        r.setRegistro(ahora);
        r.setRegistroIdUsuario(usuarioId);
        responsableService.save(r);

        log.info("Responsable {} agregado a la inscripcion {} ({})", r.getId(), inscripcionId,
                cobra ? "extra, cobrado" : "dentro del derecho");
        return new Resultado(true,
                cobra ? "Responsable agregado y cobro registrado" : "Responsable agregado",
                r.getId(), cobra, cobra ? RegistroVentaService.COSTO_RESPONSABLE_EXTRA : BigDecimal.ZERO);
    }

    public record ExtraListado(Long responsableId, String nombre, String ci, String celular,
                               String fotoUrl, java.math.BigDecimal monto, String comprobanteUrl,
                               Long inscripcionId, String entidad,
                               String agregadoPor, java.time.LocalDateTime cuando) {
    }

    @Transactional(readOnly = true)
    public List<ExtraListado> listarExtras() {
        return responsableDao.listarExtrasDeEdicionActiva().stream().map(f -> new ExtraListado(
                numero(f[0]),
                texto(f[1]),
                texto(f[2]),
                texto(f[3]),
                ruta(texto(f[4])),
                (java.math.BigDecimal) f[5],
                ruta(texto(f[6])),
                numero(f[7]),
                texto(f[8]),
                texto(f[9]),
                f[10] == null ? null : ((java.sql.Timestamp) f[10]).toLocalDateTime()
        )).toList();
    }

    private static String ruta(String archivo) {
        return (archivo == null || archivo.isBlank()) ? null : "/files/" + archivo;
    }

    private static Long numero(Object o) { return o == null ? null : ((Number) o).longValue(); }
    private static String texto(Object o) { return o == null ? null : o.toString(); }

    private static boolean vacio(String s) {
        return s == null || s.trim().isEmpty();
    }
}

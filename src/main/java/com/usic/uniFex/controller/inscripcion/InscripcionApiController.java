package com.usic.uniFex.controller.inscripcion;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.usic.uniFex.model.service.EdicionVentaService;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.dao.IResponsableDao;
import com.usic.uniFex.model.dto.InscripcionDetalleDTO;
import com.usic.uniFex.model.dto.InscripcionListadoDTO;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.service.AuditoriaService;
import com.usic.uniFex.model.service.CancelarInscripcionService;
import com.usic.uniFex.model.service.ReciboPdfService;
import com.usic.uniFex.model.service.ResponsableFotoService;
import com.usic.uniFex.model.service.RegistroVentaService;
import com.usic.uniFex.model.service.ResponsableExtraService;
import com.usic.uniFex.model.service.VendedorAsignacionService;
import com.usic.uniFex.security.AccesoPantallas;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/app/inscripciones")
@RequiredArgsConstructor
@Slf4j
public class InscripcionApiController {

    private final IInscripcionService inscripcionService;
    private final IResponsableDao responsableDao;

    private final com.usic.uniFex.model.service.ResponsableExtraService responsableExtra;
    private final RegistroVentaService registroVenta;
    private final EdicionVentaService edicionVenta;
    private final ReciboPdfService reciboPdfService;
    private final CancelarInscripcionService cancelarInscripcion;
    private final ResponsableFotoService responsableFoto;
    private final VendedorAsignacionService vendedorAsignacion;

    @GetMapping
    @PreAuthorize(Roles.VE_INSCRIPCIONES_O_PANTALLA)
    public List<InscripcionListadoDTO> listar(
            @RequestParam(defaultValue = "false") boolean canceladas) {
        return inscripcionService.listarParaTabla(canceladas,
                AccesoPantallas.soloDe(Roles.AUTORIDADES_VEN_TODAS_LAS_INSCRIPCIONES));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.VE_INSCRIPCIONES_O_PANTALLA)
    public ResponseEntity<InscripcionDetalleDTO> detalle(@PathVariable Long id) {
        Long soloDe = AccesoPantallas.soloDe(Roles.AUTORIDADES_VEN_TODAS_LAS_INSCRIPCIONES);
        if (soloDe != null) {
            Inscripcion i = inscripcionService.findById(id);
            if (i == null || !soloDe.equals(i.getRegistroIdUsuario())) return ResponseEntity.notFound().build();
        }
        InscripcionDetalleDTO d = inscripcionService.detalleParaTabla(id);
        return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Map<String, Object>> cancelar(
            @PathVariable Long id,
            @RequestBody(required = false) CancelarInscripcionService.Peticion peticion,
            @RequestHeader(value = "X-Origen", required = false) String origen) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Inscripcion i = inscripcionService.findById(id);
        if (i == null) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "La venta no existe"));
        }
        if (!usuarioId.equals(i.getRegistroIdUsuario()) && !esAdministracion()) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "mensaje", "Esta venta no es tuya"));
        }

        String motivo = peticion != null ? peticion.motivo() : null;
        CancelarInscripcionService.Resultado r =
                cancelarInscripcion.cancelar(id, motivo, usuarioId, origenDe(origen), esAdministracion());
        if (!r.ok()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", r.mensaje()));
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", true);
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("puestosLiberados", r.puestosLiberados());
        return ResponseEntity.ok(cuerpo);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> registrar(
            @RequestBody RegistroVentaService.NuevaVenta req,
            @RequestHeader(value = "X-Origen", required = false) String origen) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        if (req != null && esVendedor()) {
            List<Long> noPermitidas = vendedorAsignacion.casetasNoPermitidas(usuarioId, req.puestos());
            if (!noPermitidas.isEmpty()) {
                return ResponseEntity.status(403).body(Map.of("ok", false,
                        "mensaje", "Hay casetas que no estan asignadas a ti: " + noPermitidas));
            }
        }
        try {
            RegistroVentaService.Resultado r = registroVenta.registrar(req, usuarioId, origenDe(origen));
            if (!r.ok()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("ok", false, "mensaje", r.mensaje()));
            }
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("ok", true);
            cuerpo.put("mensaje", r.mensaje());
            cuerpo.put("inscripcionId", r.inscripcionId());
            cuerpo.put("puestos", r.puestosOcupados());
            cuerpo.put("total", r.total());
            return ResponseEntity.ok(cuerpo);
        } catch (RegistroVentaService.CasetaNoDisponibleException e) {
            return ResponseEntity.status(409).body(Map.of("ok", false, "mensaje", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "ok", false, "mensaje", e.getMessage() == null ? "Datos invalidos" : e.getMessage()));
        }
    }

    @GetMapping("/mis-pendientes")
    public ResponseEntity<?> misPendientes() {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        return ResponseEntity.ok(registroVenta.pendientesDe(usuarioId));
    }

    @PostMapping(value = "/{id}/comprobante", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> adjuntarComprobante(
            @PathVariable Long id,
            @RequestPart("archivo") MultipartFile archivo,
            @RequestParam(value = "entidadBancaria", required = false) String entidadBancaria,
            @RequestParam(value = "numComprobante", required = false) String numComprobante,
            @RequestHeader(value = "X-Origen", required = false) String origen) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        RegistroVentaService.Resultado r =
                registroVenta.adjuntarComprobante(id, archivo, entidadBancaria, numComprobante, usuarioId, origenDe(origen));
        return r.ok()
                ? ResponseEntity.ok(Map.of("ok", true, "mensaje", r.mensaje()))
                : ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", r.mensaje()));
    }

    @GetMapping("/{id}/recibo")
    public ResponseEntity<byte[]> recibo(@PathVariable Long id) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return ResponseEntity.status(401).build();

        Inscripcion i = inscripcionService.findById(id);
        if (i == null) return ResponseEntity.notFound().build();
        if (!usuarioId.equals(i.getRegistroIdUsuario()) && !esAdministracion()) {
            return ResponseEntity.status(403).build();
        }

        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            reciboPdfService.generarRecibo(id, salida);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=recibo-" + id + ".pdf")
                    .body(salida.toByteArray());
        } catch (Exception e) {
            log.error("No se pudo generar el recibo de la inscripcion {}", id, e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/{id}/responsables/{responsableId}/recibo-extra")
    public ResponseEntity<byte[]> reciboResponsableExtra(
            @PathVariable Long id,
            @PathVariable Long responsableId) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) return ResponseEntity.status(401).build();

        Inscripcion i = inscripcionService.findById(id);
        if (i == null) return ResponseEntity.notFound().build();
        if (!usuarioId.equals(i.getRegistroIdUsuario()) && !esAdministracion()) {
            return ResponseEntity.status(403).build();
        }

        var rOpt = responsableDao.findById(responsableId);
        if (rOpt.isEmpty() || !rOpt.get().getEntidad().getId().equals(i.getEntidad().getId()) || !rOpt.get().isEsExtra()) {
            return ResponseEntity.status(404).build();
        }

        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            reciboPdfService.generarReciboResponsableExtra(responsableId, salida);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=recibo-extra-" + responsableId + ".pdf")
                    .body(salida.toByteArray());
        } catch (Exception e) {
            log.error("No se pudo generar el recibo extra del responsable {}", responsableId, e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/{id}/responsables")
    public ResponseEntity<Map<String, Object>> responsables(@PathVariable Long id) {
        ResponseEntity<Map<String, Object>> veto = comprobarAcceso(id);
        if (veto != null) return veto;

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("responsables", responsableFoto.listar(id));
        cuerpo.put("fotosCompletas", responsableFoto.fotosCompletas(id));
        return ResponseEntity.ok(cuerpo);
    }

    @PatchMapping("/{id}/entidad")
    public ResponseEntity<Map<String, Object>> editarEntidad(
            @PathVariable Long id,
            @RequestBody EdicionVentaService.DatosEntidad datos) {
        ResponseEntity<Map<String, Object>> veto = comprobarAcceso(id);
        if (veto != null) return veto;

        EdicionVentaService.Resultado r = edicionVenta.editarEntidad(id, datos, usuarioActual());
        return r.ok()
                ? ResponseEntity.ok(Map.of("ok", true, "mensaje", r.mensaje()))
                : ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", r.mensaje()));
    }

    @PatchMapping("/{id}/responsables/{responsableId}")
    public ResponseEntity<Map<String, Object>> editarResponsable(
            @PathVariable Long id,
            @PathVariable Long responsableId,
            @RequestBody EdicionVentaService.DatosResponsable datos) {
        ResponseEntity<Map<String, Object>> veto = comprobarAcceso(id);
        if (veto != null) return veto;

        EdicionVentaService.Resultado r =
                edicionVenta.editarResponsable(id, responsableId, datos, usuarioActual());
        return r.ok()
                ? ResponseEntity.ok(Map.of("ok", true, "mensaje", r.mensaje()))
                : ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", r.mensaje()));
    }

    @GetMapping("/{id}/detalle")
    public ResponseEntity<Map<String, Object>> ficha(@PathVariable Long id) {
        ResponseEntity<Map<String, Object>> veto = comprobarAcceso(id);
        if (veto != null) return veto;

        Inscripcion i = inscripcionService.findById(id);
        if (i == null) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "La venta no existe"));
        }
        var e = i.getEntidad();

        Map<String, Object> entidad = new LinkedHashMap<>();
        entidad.put("nombre", e == null ? "" : e.getNombre());
        entidad.put("nit", e == null ? "" : e.getNit());
        entidad.put("descripcion", e == null ? "" : e.getDescripcion());
        entidad.put("tipo", e == null || e.getTipoEntidad() == null ? "" : e.getTipoEntidad().getNombre());
        entidad.put("representanteLegal", e == null ? "" : e.getRepresentanteLegal());
        entidad.put("ciRepresentante", e == null ? "" : e.getCiRepresentante());
        entidad.put("celularRepresentante", e == null ? "" : e.getCelularRepresentante());

        Map<String, Object> pago = new LinkedHashMap<>();
        pago.put("contado", i.isPagoContado());
        pago.put("entidadBancaria", i.getEntidadBancaria());
        pago.put("numComprobante", i.getNumComprobante());
        pago.put("conComprobante", i.getImgComprobante() != null && !i.getImgComprobante().isBlank());
        pago.put("comprobanteUrl", i.getImgComprobante() == null || i.getImgComprobante().isBlank()
                ? null : "/files/" + i.getImgComprobante());

        List<Map<String, Object>> casetas = i.getInscripcionPuestos().stream()
                .filter(ip -> ip.getPuesto() != null
                        && !"X".equalsIgnoreCase(String.valueOf(ip.getEstado())))
                .map(ip -> {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("codigo", ip.getPuesto().getCodigo());
                    c.put("categoria", ip.getPuesto().getCategoria() == null
                            ? "" : ip.getPuesto().getCategoria().getNombre());
                    c.put("color", ip.getPuesto().getCategoria() == null
                            ? null : ip.getPuesto().getCategoria().getColor());
                    return c;
                })
                .toList();

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", true);
        cuerpo.put("id", i.getId());
        cuerpo.put("entidad", entidad);
        cuerpo.put("pago", pago);
        cuerpo.put("casetas", casetas);
        cuerpo.put("responsables", responsableFoto.listar(id));
        cuerpo.put("fotosCompletas", responsableFoto.fotosCompletas(id));
        return ResponseEntity.ok(cuerpo);
    }

    @PostMapping(value = "/{id}/responsables/{responsableId}/foto",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> subirFotoResponsable(
            @PathVariable Long id,
            @PathVariable Long responsableId,
            @RequestPart("archivo") MultipartFile archivo) {
        ResponseEntity<Map<String, Object>> veto = comprobarAcceso(id);
        if (veto != null) return veto;

        ResponsableFotoService.Resultado r =
                responsableFoto.guardar(id, responsableId, archivo, usuarioActual());
        return respuestaFoto(r);
    }

    @DeleteMapping("/{id}/responsables/{responsableId}/foto")
    public ResponseEntity<Map<String, Object>> quitarFotoResponsable(
            @PathVariable Long id, @PathVariable Long responsableId) {
        ResponseEntity<Map<String, Object>> veto = comprobarAcceso(id);
        if (veto != null) return veto;

        return respuestaFoto(responsableFoto.quitar(id, responsableId, usuarioActual()));
    }

    private ResponseEntity<Map<String, Object>> respuestaFoto(ResponsableFotoService.Resultado r) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("responsable", r.responsable());
        return r.ok() ? ResponseEntity.ok(cuerpo) : ResponseEntity.badRequest().body(cuerpo);
    }

    @GetMapping("/responsables-extra")
    @PreAuthorize(Roles.VE_INSCRIPCIONES)
    public List<ResponsableExtraService.ExtraListado> responsablesExtra() {
        return responsableExtra.listarExtras();
    }

    @GetMapping("/{id}/responsables/cupo")
    public ResponseEntity<?> cupoResponsables(@PathVariable Long id) {
        ResponseEntity<Map<String, Object>> veto = comprobarAcceso(id);
        if (veto != null) return veto;
        ResponsableExtraService.Cupo c = responsableExtra.cupoDe(id);
        if (c == null) return ResponseEntity.status(404).body(Map.of("ok", false));
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "casetas", c.casetas(),
                "derecho", c.derecho(),
                "registrados", c.registrados(),
                "extras", c.extras(),
                "dentroDelDerecho", c.dentroDelDerecho(),
                "costoSiguiente", c.costoSiguiente()));
    }

    @PostMapping(value = "/{id}/responsables", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> agregarResponsable(
            @PathVariable Long id,
            @RequestParam("nombre") String nombre,
            @RequestParam(value = "paterno", required = false) String paterno,
            @RequestParam(value = "materno", required = false) String materno,
            @RequestParam("ci") String ci,
            @RequestParam(value = "correo", required = false) String correo,
            @RequestParam(value = "celular", required = false) String celular,
            @RequestPart(value = "comprobante", required = false) MultipartFile comprobante) {
        ResponseEntity<Map<String, Object>> veto = comprobarAcceso(id);
        if (veto != null) return veto;

        Inscripcion i = inscripcionService.findById(id);
        Long usuarioId = usuarioActual();

        if (esSuperUsuario() && i != null && i.getRegistroIdUsuario() != null) {
            usuarioId = i.getRegistroIdUsuario();
        }

        ResponsableExtraService.Resultado r = responsableExtra.agregar(id,
                new ResponsableExtraService.NuevoResponsable(nombre, paterno, materno, ci, correo, celular),
                comprobante, usuarioId);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("responsableId", r.responsableId());
        cuerpo.put("cobrado", r.cobrado());
        cuerpo.put("monto", r.monto());
        return r.ok() ? ResponseEntity.ok(cuerpo) : ResponseEntity.badRequest().body(cuerpo);
    }

    private boolean esSuperUsuario() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream()
                .anyMatch(auth -> "ROLE_SUPER_USUARIO".equals(auth.getAuthority()));
    }

    private ResponseEntity<Map<String, Object>> comprobarAcceso(Long inscripcionId) {
        Long usuarioId = usuarioActual();
        if (usuarioId == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
        }
        Inscripcion i = inscripcionService.findById(inscripcionId);
        if (i == null) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "La venta no existe"));
        }
        if (!usuarioId.equals(i.getRegistroIdUsuario()) && !esAdministracion() && !esVerificador()) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "mensaje", "Esta venta no es tuya"));
        }
        return null;
    }

    private boolean esVerificador() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_VERIFICADOR"::equals);
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }

    private boolean esVendedor() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMINISTRATIVO".equals(a.getAuthority()));
    }

    private static String origenDe(String origen) {
        return AuditoriaService.ORIGEN_APK.equalsIgnoreCase(origen == null ? "" : origen.trim())
                ? AuditoriaService.ORIGEN_APK
                : AuditoriaService.ORIGEN_WEB;
    }

    private boolean esAdministracion() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return false;
        return a.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(Roles.AUTORIDADES_ADMINISTRA::contains);
    }
}

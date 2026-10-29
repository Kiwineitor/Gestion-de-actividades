package com.usic.uniFex.controller.credenciales;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.usic.uniFex.model.dto.CredencialDTO;
import com.usic.uniFex.model.dto.PlantillaCredencial;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.service.CredencialCodigoService;
import com.usic.uniFex.model.service.CredencialPdfService;
import com.usic.uniFex.model.service.CredencialImagenService;
import com.usic.uniFex.model.service.CredencialService;
import com.usic.uniFex.model.service.ReciboPdfService;
import com.usic.uniFex.model.service.WhatsAppService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.IService.ICategoriaService;
import com.usic.uniFex.model.repository.FuncionesInscripcion;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/app/credenciales")
@RequiredArgsConstructor
@Slf4j
public class CredencialesApiController {

    private final FuncionesInscripcion funcionesInscripcion;
    private final ICategoriaService categoriaService;
    private final com.usic.uniFex.model.dao.ICategoriaDao categoriaDao;
    private final CredencialService credencialService;
    private final CredencialPdfService pdfService;
    private final CredencialImagenService imagenService;
    private final CredencialCodigoService codigos;
    private final ReciboPdfService reciboPdfService;
    private final WhatsAppService whatsApp;
    private final IInscripcionService inscripcionService;
    private final com.usic.uniFex.model.dao.CredencialImpresionDao impresiones;
    private final com.usic.uniFex.model.service.CredencialDatosService datosService;

    @Value("${unifex.publico.base-url:}")
    private String baseUrlPublica;

    @GetMapping("/plantillas")
    @PreAuthorize(Roles.USA_CREDENCIALES_O_PANTALLA)
    public List<Map<String, Object>> plantillas() {
        return PlantillaCredencial.catalogo().stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id());
            m.put("etiqueta", p.etiqueta());
            m.put("detalle", p.detalle());
            m.put("requiereFoto", p.requiereFoto());
            m.put("proporcion", pdfService.proporcion(p));
            return m;
        }).toList();
    }

    @GetMapping("/categorias")
    @PreAuthorize(Roles.VERIFICA_CREDENCIALES)
    public List<Map<String, Object>> categorias() {
        return categoriaDao.deEdicionActiva().stream()
                .map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("nombre", c.getNombre());
                    return m;
                })
                .toList();
    }

    @GetMapping("/inscripciones-por-categoria")
    @PreAuthorize(Roles.VERIFICA_CREDENCIALES)
    public List<Map<String, Object>> inscripcionesPorCategoria() {
        return funcionesInscripcion.fn_inscripciones_por_categoria();
    }

    @GetMapping("/inscripciones/{id}")
    @PreAuthorize(Roles.VERIFICA_CREDENCIALES)
    public ResponseEntity<Map<String, Object>> detalleInscripcion(@PathVariable Long id) {
        List<Map<String, Object>> lista = funcionesInscripcion.obtener_inscripcion_detalle(id);
        if (lista == null || lista.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> primera = lista.get(0);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("id", primera.get("id"));
        cuerpo.put("fechaCompra", primera.get("fecha_compra"));
        cuerpo.put("nombreEntidad", primera.get("nombre_entidad"));
        cuerpo.put("representanteLegal", primera.get("representante_legal"));
        cuerpo.put("ciRepresentante", primera.get("ci_representante"));
        cuerpo.put("nit", primera.get("nit"));
        cuerpo.put("descripcion", primera.get("descripcion"));
        cuerpo.put("objeto", primera.get("objeto"));
        cuerpo.put("datoqr", primera.get("datoqr"));
        List<Map<String, Object>> responsables = lista.stream().map(row -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("nombrePersona", row.get("nombre_persona"));
            r.put("paternoPersona", row.get("paterno_persona"));
            r.put("maternoPersona", row.get("materno_persona"));
            r.put("ciPersona", row.get("ci_persona"));
            r.put("fotoPersona", row.get("foto_persona"));
            r.put("esTitular", row.get("es_titular") != null ? row.get("es_titular") : false);
            return r;
        }).toList();
        cuerpo.put("responsables", responsables);
        return ResponseEntity.ok(cuerpo);
    }

    @GetMapping
    @PreAuthorize(Roles.USA_CREDENCIALES_O_PANTALLA)
    public List<Map<String, Object>> listar() {
        Long soloMias = alcanceDelUsuario();
        Map<Long, Object[]> impresas = new LinkedHashMap<>();
        for (Object[] f : impresiones.resumen()) {
            impresas.put(((Number) f[0]).longValue(), f);
        }

        return credencialService.listar(soloMias).stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("responsableId", c.responsableId());
            m.put("codigo", c.codigo());
            m.put("nombre", c.nombre());
            m.put("ci", c.ci());
            m.put("fotoUrl", c.fotoUrl());
            m.put("esTitular", c.esTitular());
            m.put("cargo", c.cargo());
            m.put("entidad", c.entidad());
            m.put("rubro", c.rubro());
            m.put("categoria", c.categoria());
            m.put("categoriaId", c.categoriaId());
            m.put("casetas", c.casetas());
            m.put("inscripcionId", c.inscripcionId());
            m.put("conComprobante", c.conComprobante());
            m.put("conFoto", c.conFoto());
            m.put("esExtra", c.esExtra());
            m.put("montoExtra", c.montoExtra());
            m.put("comprobanteExtraUrl", c.comprobanteExtraUrl());
            m.put("sinCosto", c.sinCosto());
            m.put("requiereComprobante", c.requiereComprobante());
            Map<String, Object> listo = new LinkedHashMap<>();
            Map<String, Object> falta = new LinkedHashMap<>();
            for (PlantillaCredencial p : PlantillaCredencial.catalogo()) {
                listo.put(p.id(), c.apto(p.id()));
                falta.put(p.id(), c.faltantes(p.id()));
            }
            m.put("listo", listo);
            m.put("faltantes", falta);

            Object[] imp = impresas.get(c.responsableId());
            m.put("impresa", imp != null);
            m.put("vecesImpresa", imp == null ? 0 : ((Number) imp[1]).intValue());
            m.put("ultimaImpresion", imp == null ? null : String.valueOf(imp[2]));
            m.put("impresaIncompleta", imp != null && Boolean.TRUE.equals(imp[3]));
            return m;
        }).toList();
    }

    public record PeticionPdf(List<Long> responsables, String plantilla, Double anchoCm,
                              Boolean forzar) {
    }

    public record PeticionWhatsApp(Long inscripcionId, List<Long> responsables, Boolean incluirRecibo) {
    }

    @PostMapping("/pdf")
    @PreAuthorize(Roles.USA_CREDENCIALES_O_PANTALLA)
    public ResponseEntity<byte[]> pdf(@RequestBody(required = false) PeticionPdf req) {
        String pedida = req == null ? null : req.plantilla();
        PlantillaCredencial disposicion;
        if (pedida == null || pedida.isBlank()) {
            disposicion = PlantillaCredencial.porDefecto();
        } else {
            var hallada = PlantillaCredencial.de(pedida);
            if (hallada.isEmpty()) {
                log.warn("Se pidio imprimir con la plantilla desconocida '{}'", pedida);
                return ResponseEntity.badRequest()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(("No existe la plantilla '" + pedida.trim() + "'.")
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            disposicion = hallada.get();
        }
        String plantilla = disposicion.id();
        boolean forzar = req != null && Boolean.TRUE.equals(req.forzar());
        Long soloMias = alcanceDelUsuario();

        List<Long> ids = req == null ? null : req.responsables();
        List<CredencialDTO> elegidas = (ids == null || ids.isEmpty())
                ? credencialService.listarAptas(plantilla, soloMias)
                : credencialService.porResponsables(ids);

        if (soloMias != null) {
            for (CredencialDTO c : elegidas) {
                if (!credencialService.esDeUsuario(c.responsableId(), soloMias)) {
                    log.warn("El usuario {} pidio imprimir la credencial {}, que no es de una venta suya",
                            soloMias, c.responsableId());
                    return ResponseEntity.status(403)
                            .contentType(MediaType.TEXT_PLAIN)
                            .body(("Solo puedes generar las credenciales de las ventas que "
                                    + "registraste.").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }

        List<CredencialDTO> imprimibles = forzar
                ? elegidas
                : elegidas.stream().filter(c -> c.apto(plantilla)).toList();
        if (imprimibles.isEmpty()) {
            return ResponseEntity.status(409)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Ninguna de las credenciales pedidas cumple los requisitos para la "
                            + "plantilla elegida. Falta el comprobante"
                            + (CredencialDTO.requiereFoto(plantilla) ? ", o la foto del responsable." : "."))
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        byte[] pdf = pdfService.generar(
                imprimibles,
                disposicion,
                req == null || req.anchoCm() == null
                        ? CredencialPdfService.ANCHO_CM_POR_DEFECTO : req.anchoCm(),
                raizPublica(),
                codigos);

        Long quien = usuarioActual();
        for (CredencialDTO c : imprimibles) {
            impresiones.registrar(c.responsableId(), c.inscripcionId(), plantilla,
                    String.join(", ", c.faltantes(plantilla)), quien);
        }

        String nombre = imprimibles.size() == 1
                ? "credencial-" + imprimibles.get(0).codigo() + ".pdf"
                : "credenciales-" + imprimibles.size() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"" + nombre + "\"")
                .body(pdf);
    }

    @PostMapping(value = "/pdf-2up", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(Roles.USA_CREDENCIALES_O_PANTALLA)
    public ResponseEntity<byte[]> pdf2up(@RequestBody(required = false) PeticionPdf2up req) {
        String plantilla = req == null || req.plantilla() == null || req.plantilla().isBlank()
                ? "EXPOSITOR" : req.plantilla();
        double anchoCm = req == null || req.anchoCm() == null ? 10.0 : req.anchoCm();
        List<Long> ids = req == null ? null : req.responsables();

        List<CredencialDTO> elegidas = (ids == null || ids.isEmpty())
                ? credencialService.listarAptas("EXPOSITOR", alcanceDelUsuario())
                : credencialService.porResponsables(ids);

        if (elegidas.isEmpty()) {
            return ResponseEntity.status(409)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("No hay credenciales aptas para imprimir en formato 2-up."
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        byte[] pdf = pdfService.generarDuplex4(
                elegidas,
                PlantillaCredencial.actual(PlantillaCredencial.EXPOSITOR),
                req == null || req.anchoCm() == null
                        ? com.usic.uniFex.model.service.CredencialPdfService.CRED_ANCHO_CM
                        : req.anchoCm(),
                com.usic.uniFex.model.service.CredencialPdfService.CRED_ALTO_CM,
                raizPublica(),
                codigos);

        String nombre = elegidas.size() == 1
                ? "credencial-2up.pdf"
                : "credenciales-2up-" + elegidas.size() + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"" + nombre + "\"")
                .body(pdf);
    }

    public record PeticionPdf2up(List<Long> responsables, String plantilla, Double anchoCm) {
    }

    @GetMapping("/{responsableId}/virtual")
    @PreAuthorize(Roles.USA_CREDENCIALES_O_PANTALLA)
    public ResponseEntity<byte[]> virtual(@PathVariable Long responsableId,
                                           @RequestParam(value = "forzar", defaultValue = "false") boolean forzar) {
        Long soloMias = alcanceDelUsuario();
        if (soloMias != null && !credencialService.esDeUsuario(responsableId, soloMias)) {
            return ResponseEntity.status(403).build();
        }
        CredencialDTO c = credencialService.porResponsable(responsableId).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();

        PlantillaCredencial p = PlantillaCredencial.actual(PlantillaCredencial.CREDENCIAL_VIRTUAL);
        if (!forzar && !c.apto(p.id())) {
            return ResponseEntity.status(409)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Todavía no se puede emitir esta credencial: "
                            + String.join(" y ", c.faltantes(p.id())) + ".")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        byte[] png = imagenService.generar(c, p, raizPublica());
        impresiones.registrar(c.responsableId(), c.inscripcionId(), p.id(),
                String.join(", ", c.faltantes(p.id())), usuarioActual());

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("Content-Disposition",
                        "inline; filename=\"credencial-" + c.codigo() + ".png\"")
                .body(png);
    }

    @PostMapping("/whatsapp")
    @PreAuthorize(Roles.USA_CREDENCIALES_O_PANTALLA)
    public ResponseEntity<Map<String, Object>> whatsapp(@RequestBody(required = false) PeticionWhatsApp req) {
        log.info("[WHATSAPP-REENVIO] Peticion recibida inscripcion={} responsables={}",
                req == null ? null : req.inscripcionId(), req == null ? null : req.responsables());
        if (req == null || req.inscripcionId() == null) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "Falta la inscripción"));
        }
        if (!whatsApp.habilitado()) {
            return ResponseEntity.status(409).body(Map.of("ok", false, "mensaje", "WhatsApp no está configurado"));
        }

        Inscripcion inscripcion = inscripcionService.findById(req.inscripcionId());
        if (inscripcion == null || inscripcion.getEntidad() == null) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "La inscripción no existe"));
        }
        Long soloMias = alcanceDelUsuario();
        if (soloMias != null && !soloMias.equals(inscripcion.getRegistroIdUsuario())) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "mensaje", "Solo puedes reenviar tus ventas"));
        }

        String celular = normalizarCelular(inscripcion.getEntidad().getCelularRepresentante());
        log.info("[WHATSAPP-REENVIO] Inscripcion={} entidad='{}' celularOriginal='{}' celularNormalizado='{}'",
                req.inscripcionId(), inscripcion.getEntidad().getNombre(),
                inscripcion.getEntidad().getCelularRepresentante(), celular);
        if (celular == null || celular.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "La venta no tiene celular del cliente"));
        }

        PlantillaCredencial plantilla = PlantillaCredencial.actual(PlantillaCredencial.CREDENCIAL_VIRTUAL);
        List<Long> seleccion = req.responsables() == null ? List.of() : req.responsables();
        var todas = credencialService.porInscripcion(req.inscripcionId());
        log.info("[WHATSAPP-REENVIO] Credenciales de inscripcion={} total={} seleccion={}",
                req.inscripcionId(), todas.size(), seleccion);
        List<CredencialDTO> credenciales = todas.stream()
                .filter(c -> seleccion.isEmpty() || seleccion.contains(c.responsableId()))
                .peek(c -> log.info("[WHATSAPP-REENVIO] Candidata responsable={} nombre='{}' fotoUrl='{}' conFoto={} conComprobante={} aptaVirtual={}",
                        c.responsableId(), c.nombre(), c.fotoUrl(), c.conFoto(), c.conComprobante(), c.apto(plantilla.id())))
                .filter(c -> c.apto(plantilla.id()))
                .toList();
        log.info("[WHATSAPP-REENVIO] Credenciales aptas inscripcion={} cantidad={}",
                req.inscripcionId(), credenciales.size());
        if (credenciales.isEmpty()) {
            return ResponseEntity.status(409).body(Map.of("ok", false,
                    "mensaje", "No hay credenciales listas para reenviar. Falta comprobante o foto."));
        }

        try {
            byte[] recibo = null;
            if (Boolean.TRUE.equals(req.incluirRecibo())) {
                ByteArrayOutputStream salida = new ByteArrayOutputStream();
                reciboPdfService.generarRecibo(req.inscripcionId(), salida);
                recibo = salida.toByteArray();
                log.info("[WHATSAPP-REENVIO] Recibo generado inscripcion={} bytes={}",
                        req.inscripcionId(), recibo.length);
            }
            List<WhatsAppService.ReciboExtra> recibosExtra = new java.util.ArrayList<>();
            for (CredencialDTO credencial : credenciales) {
                if (!credencial.esExtra()) continue;
                ByteArrayOutputStream salida = new ByteArrayOutputStream();
                reciboPdfService.generarReciboResponsableExtra(credencial.responsableId(), salida);
                recibosExtra.add(new WhatsAppService.ReciboExtra(credencial.responsableId(),
                        credencial.nombre(), salida.toByteArray()));
            }
            List<byte[]> imagenes = credenciales.stream()
                    .map(c -> imagenService.generar(c, plantilla, raizPublica()))
                    .toList();
            log.info("[WHATSAPP-REENVIO] Imagenes de credencial generadas inscripcion={} cantidad={}",
                    req.inscripcionId(), imagenes.size());
            whatsApp.enviarBienvenidaVentaConPdfs(celular, inscripcion.getEntidad().getNombre(),
                    req.inscripcionId(), recibo, imagenes, raizPublica(), recibosExtra);

            return ResponseEntity.ok(Map.of("ok", true,
                    "mensaje", "Reenvío enviado por WhatsApp",
                    "credenciales", credenciales.size()));
        } catch (Exception e) {
            log.warn("No se pudo reenviar WhatsApp de inscripcion {}: {}", req.inscripcionId(), e.getMessage());
            return ResponseEntity.status(500).body(Map.of("ok", false,
                    "mensaje", "No se pudo reenviar por WhatsApp"));
        }
    }

    @GetMapping("/{responsableId}/datos")
    @PreAuthorize(Roles.USA_CREDENCIALES_O_PANTALLA)
    public ResponseEntity<Map<String, Object>> datos(@PathVariable Long responsableId) {
        if (!alcanza(responsableId)) return ResponseEntity.status(403).build();
        Map<String, Object> d = datosService.datos(responsableId);
        return d == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(d);
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{responsableId}/datos")
    @PreAuthorize(Roles.USA_CREDENCIALES_O_PANTALLA)
    public ResponseEntity<Map<String, Object>> corregirDatos(
            @PathVariable Long responsableId,
            @RequestBody com.usic.uniFex.model.service.CredencialDatosService.Cambio cambio) {
        if (!alcanza(responsableId)) return ResponseEntity.status(403).build();
        return respuestaDatos(datosService.actualizar(responsableId, cambio, usuarioActual()));
    }

    @PostMapping(value = "/{responsableId}/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.USA_CREDENCIALES_O_PANTALLA)
    public ResponseEntity<Map<String, Object>> cambiarFoto(
            @PathVariable Long responsableId,
            @RequestParam("archivo") org.springframework.web.multipart.MultipartFile archivo) {
        if (!alcanza(responsableId)) return ResponseEntity.status(403).build();
        return respuestaDatos(datosService.cambiarFoto(responsableId, archivo, usuarioActual()));
    }

    @GetMapping("/{responsableId}/cambios")
    @PreAuthorize(Roles.USA_CREDENCIALES_O_PANTALLA)
    public List<Map<String, Object>> cambios(@PathVariable Long responsableId) {
        return alcanza(responsableId) ? datosService.historial(responsableId) : List.of();
    }

    private boolean alcanza(Long responsableId) {
        Long soloMias = alcanceDelUsuario();
        return soloMias == null || credencialService.esDeUsuario(responsableId, soloMias);
    }

    private ResponseEntity<Map<String, Object>> respuestaDatos(
            com.usic.uniFex.model.service.CredencialDatosService.Resultado r) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("datos", r.datos());
        return ResponseEntity.status(r.ok() ? 200 : 400).body(cuerpo);
    }

    @GetMapping("/{responsableId}/impresiones")
    @PreAuthorize(Roles.USA_CREDENCIALES_O_PANTALLA)
    public List<Map<String, Object>> impresiones(@PathVariable Long responsableId) {
        Long soloMias = alcanceDelUsuario();
        if (soloMias != null && !credencialService.esDeUsuario(responsableId, soloMias)) {
            return List.of();
        }
        return impresiones.historial(responsableId).stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cuando", String.valueOf(f[0]));
            m.put("plantilla", f[1]);
            m.put("faltaba", f[2]);
            m.put("usuario", f[3]);
            return m;
        }).toList();
    }

    private String raizPublica() {
        if (baseUrlPublica != null && !baseUrlPublica.isBlank()) return baseUrlPublica.trim();
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    private static String normalizarCelular(String celular) {
        if (celular == null || celular.isBlank()) return null;
        String n = celular.replaceAll("[^0-9]", "");
        if (n.startsWith("0")) n = n.substring(1);
        return n.length() == 8 ? "591" + n : n;
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }

    private Long alcanceDelUsuario() {
        return (esAdministracion() || esVerificador()) ? null : usuarioActual();
    }

    private boolean esVerificador() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_VERIFICADOR"::equals);
    }

    private boolean esAdministracion() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return false;
        return a.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(Roles.AUTORIDADES_ADMINISTRA::contains);
    }
}

package com.usic.uniFex.controller.responsables;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.anotacion.ValidarUsuarioAutenticado;
import com.usic.uniFex.model.IService.IEntidadService;
import com.usic.uniFex.model.IService.IInscripcionService;
import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.IService.IResponsableService;
import com.usic.uniFex.model.IService.ITipoEntidadService;
import com.usic.uniFex.model.entity.Entidad;
import com.usic.uniFex.model.entity.Inscripcion;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Responsable;
import com.usic.uniFex.model.entity.Usuario;
import com.usic.uniFex.model.service.FileStorageService;
import com.usic.uniFex.model.service.FileStorageService.Bucket;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/administracion/responsables")
public class ResponsablesController {

    private final IResponsableService responsableService;

    private final IInscripcionService inscripcionService;
    private final ITipoEntidadService tipoEntidadService;

    private final IEntidadService entidadService;

    private final IPersonaService personaService;

    private final FileStorageService storage;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista_responsables(Model model) {
        model.addAttribute("entidades", entidadService.findAll());
        return "responsables/vista";
    }

    @RequestMapping(value = "/tabla-registros", method = { RequestMethod.GET, RequestMethod.POST })
    public String tabla_responsbales(Model model) {
        model.addAttribute("responsables", responsableService.listarParaTabla());
        return "responsables/tabla_registro";
    }

    @PostMapping(value = "/registrar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> registrar(
            @RequestParam("idEntidad") Long idEntidad,
            @RequestParam("nombre") String nombre,
            @RequestParam("paterno") String paterno,
            @RequestParam(value = "materno", required = false) String materno,
            @RequestParam("ci") String ci,
            @RequestParam(value = "correo", required = false) String correo,
            @RequestParam("celular") String celular,
            @RequestParam(value = "foto", required = false) MultipartFile foto,
            HttpServletRequest request,
            Model model) {
        Entidad entidad = entidadService.findById(idEntidad);
        if (entidad == null) {
            model.addAttribute("error", "La entidad seleccionada no existe.");
            model.addAttribute("entidades", entidadService.findAll());
            return ResponseEntity.ok("No está habilitada esta entidad");
        }

        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        Long idUsuario = (usuario != null ? usuario.getId() : null);
        Date ahora = new Date();

        Persona persona = new Persona();
        persona.setNombre(nombre != null ? nombre.trim() : null);
        persona.setPaterno(paterno != null ? paterno.trim() : null);
        persona.setMaterno(materno != null ? materno.trim() : null);
        persona.setCi(ci != null ? ci.trim() : null);
        persona.setCorreo(correo != null ? correo.trim() : null);
        persona.setCelular(celular != null ? celular.trim() : null);

        persona.setEstado("RESPONSABLE");
        persona.setRegistro(ahora);
        persona.setRegistroIdUsuario(idUsuario);
        persona.setModificacion(ahora);
        persona.setModificacionIdUsuario(idUsuario);

        if (foto != null && !foto.isEmpty()) {
            try {
                String nombreBase = (persona.getNombreCompleto().isBlank() ? "responsable"
                        : persona.getNombreCompleto())
                        + "_" + (persona.getCi() == null ? "" : persona.getCi());
                String fotoRel = storage.save(foto, Bucket.RESPONSABLES, nombreBase);
                persona.setFoto(fotoRel);
            } catch (IOException e) {
                log.warn("No se pudo guardar foto del responsable: {}", e.getMessage());
                persona.setFoto(null);
            }
        }

        personaService.save(persona);

        Responsable responsable = new Responsable();
        responsable.setPersona(persona);
        responsable.setEntidad(entidad);

        responsable.setEstado("RESPONSABLE");
        responsable.setRegistro(ahora);
        responsable.setRegistroIdUsuario(idUsuario);
        responsable.setModificacion(ahora);
        responsable.setModificacionIdUsuario(idUsuario);

        responsableService.save(responsable);

        return ResponseEntity.ok("Se realizó el registro correctamente");
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/vistaRA")
    public String vista_responsablesR() {
        return "responsable_rueda/vista";
    }

    @RequestMapping(value = "/tabla-registrosR", method = { RequestMethod.GET, RequestMethod.POST })
    public String tabla_responsbalesR(Model model) {
        model.addAttribute("responsables", responsableService.listarVista());
        return "responsable_rueda/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/formulario-edit/{id_inscripcion}")
    public String modalResponsable(@PathVariable("id_inscripcion") Long id_inscripcion, Model model) {
        model.addAttribute("inscripcion", inscripcionService.findById(id_inscripcion));
        model.addAttribute("tipoEntidades", tipoEntidadService.findAll());
        return "publico/formulario :: modalContent";
    }

    @PostMapping("/modificar-inscripcion")
    public ResponseEntity<String> modificar(
            @ModelAttribute Inscripcion inscripcion,
            @RequestParam(value = "comprobante", required = false) MultipartFile comprobante,
            @RequestParam Map<String, MultipartFile> fotos) {

        Inscripcion existente = inscripcionService.findById(inscripcion.getId());

        existente.setEntidadBancaria(inscripcion.getEntidadBancaria());
        existente.setNumComprobante(inscripcion.getNumComprobante());

        Entidad entidad = existente.getEntidad();
        if (entidad == null)
            entidad = new Entidad();

        Entidad entidadReq = inscripcion.getEntidad();
        if (entidadReq != null) {
            entidad.setNombre(entidadReq.getNombre());
            entidad.setNit(entidadReq.getNit());
            entidad.setDescripcion(entidadReq.getDescripcion());
            entidad.setObjeto(entidadReq.getObjeto());
        }
        existente.setEntidad(entidad);

        if (comprobante != null && !comprobante.isEmpty()) {
            try {
                String compRel = storage.save(comprobante, Bucket.COMPROBANTES, entidad.getNombre());
                existente.setImgComprobante(compRel);
            } catch (IOException e) {
                existente.setImgComprobante(null);
            }
        }

        List<Responsable> entrantes = (entidadReq != null && entidadReq.getResponsables() != null)
                ? entidadReq.getResponsables()
                : List.of();

        if (entidad.getResponsables() == null)
            entidad.setResponsables(new ArrayList<>());

        Map<Long, Responsable> mapExistentes = entidad.getResponsables().stream()
                .filter(r -> r.getId() != null)
                .collect(Collectors.toMap(Responsable::getId, r -> r));

        List<Responsable> resultado = new ArrayList<>();

        for (int i = 0; i < entrantes.size(); i++) {
            Responsable rReq = entrantes.get(i);
            if (rReq.getPersona() == null || rReq.getPersona().getNombre() == null ||
                    rReq.getPersona().getNombre().trim().isEmpty()) {
                continue;
            }

            MultipartFile foto = fotos.get("fotoResp_" + i);

            if (rReq.getId() != null && mapExistentes.containsKey(rReq.getId())) {
                Responsable rDb = mapExistentes.get(rReq.getId());

                Persona pDb = rDb.getPersona();
                if (pDb == null)
                    pDb = new Persona();
                Persona pReq = rReq.getPersona();

                pDb.setNombre(pReq.getNombre());
                pDb.setPaterno(pReq.getPaterno());
                pDb.setMaterno(pReq.getMaterno());
                pDb.setCi(pReq.getCi());
                pDb.setCorreo(pReq.getCorreo());
                pDb.setCelular(pReq.getCelular());

                if (foto != null && !foto.isEmpty()) {
                    try {
                        String path = storage.save(foto, Bucket.RESPONSABLES,
                                (pDb.getNombre() + "_" + pDb.getPaterno() + "_" + pDb.getMaterno()).trim());
                        pDb.setFoto(path);
                    } catch (IOException e) {
                    }
                }

                personaService.save(pDb);
                rDb.setPersona(pDb);
                rDb.setEntidad(entidad);

                resultado.add(rDb);

            } else {
                Persona pReq = rReq.getPersona();
                Persona p = new Persona();
                if (pReq != null) {
                    p.setNombre(pReq.getNombre());
                    p.setPaterno(pReq.getPaterno());
                    p.setMaterno(pReq.getMaterno());
                    p.setCi(pReq.getCi());
                    p.setCorreo(pReq.getCorreo());
                    p.setCelular(pReq.getCelular());
                }

                if (foto != null && !foto.isEmpty()) {
                    try {
                        String path = storage.save(foto, Bucket.RESPONSABLES,
                                (p.getNombre() + "_" + p.getPaterno() + "_" + p.getMaterno()).trim());
                        p.setFoto(path);
                    } catch (IOException e) {
                    }
                }

                p = personaService.save(p);

                Responsable nuevo = new Responsable();
                nuevo.setPersona(p);
                nuevo.setEntidad(entidad);

                resultado.add(nuevo);
            }
        }

        entidad.setResponsables(resultado);

        inscripcionService.save(existente);

        return ResponseEntity.ok("Se realizó la modificación correctamente");
    }

}
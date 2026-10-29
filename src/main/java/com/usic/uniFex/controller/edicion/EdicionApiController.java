package com.usic.uniFex.controller.edicion;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.dao.IEdicionDao;
import com.usic.uniFex.model.dto.EdicionDTO;
import com.usic.uniFex.model.service.ActividadService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/ediciones")
@RequiredArgsConstructor
public class EdicionApiController {

    private final IEdicionDao edicionDao;
    private final ActividadService actividades;

    @GetMapping
    public List<EdicionDTO> listar() {
        return edicionDao.findAllByOrderByAnioDesc().stream()
                .map(EdicionDTO::de)
                .toList();
    }

    @GetMapping("/resumen")
    @PreAuthorize(Roles.ADMINISTRA)
    public List<Map<String, Object>> resumen() {
        return actividades.listar();
    }

    @PostMapping
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<Map<String, Object>> crear(@RequestBody ActividadService.Datos datos) {
        return responder(actividades.crear(datos, usuarioActual()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<Map<String, Object>> editar(@PathVariable Long id, @RequestBody ActividadService.Datos datos) {
        return responder(actividades.editar(id, datos));
    }

    @PostMapping("/{id}/activar")
    @PreAuthorize(Roles.SOLO_SUPER_USUARIO)
    public ResponseEntity<Map<String, Object>> activar(@PathVariable Long id) {
        return responder(actividades.activar(id));
    }

    @PostMapping(value = "/{id}/portada", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<Map<String, Object>> subirPortada(@PathVariable Long id, @RequestParam("archivo") MultipartFile archivo)
            throws IOException {
        return responder(actividades.subirPortada(id, archivo));
    }

    @DeleteMapping("/{id}/portada")
    @PreAuthorize(Roles.ADMINISTRA)
    public ResponseEntity<Map<String, Object>> quitarPortada(@PathVariable Long id) {
        return responder(actividades.quitarPortada(id));
    }

    private ResponseEntity<Map<String, Object>> responder(ActividadService.Resultado r) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", r.ok());
        cuerpo.put("mensaje", r.mensaje());
        cuerpo.put("id", r.id());
        return ResponseEntity.status(r.ok() ? 200 : 409).body(cuerpo);
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }
}

package com.usic.uniFex.controller.usuario;

import java.util.List;
import java.util.Map;

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

import com.usic.uniFex.model.dto.UsuarioDTO;
import com.usic.uniFex.model.service.GestionPersonaService;
import com.usic.uniFex.model.service.GestionUsuarioService;
import com.usic.uniFex.model.service.GestionUsuarioService.Actor;
import com.usic.uniFex.model.service.GestionUsuarioService.Resultado;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/usuarios")
@RequiredArgsConstructor
@PreAuthorize(Roles.GESTIONA_USUARIOS)
public class UsuarioApiController {

    private static final int MAX_PERSONAS = 30;

    private final GestionUsuarioService gestion;
    private final GestionPersonaService gestionPersona;

    @GetMapping
    public List<UsuarioDTO> listar() {
        return gestion.listar().stream().map(UsuarioDTO::de).toList();
    }

    @GetMapping("/personas")
    public List<GestionPersonaService.Seleccionable> personas(
            @RequestParam(value = "q", required = false) String q) {
        return gestionPersona.buscarParaSelector(q, MAX_PERSONAS);
    }

    @GetMapping("/personas/por-ci")
    public Map<String, Object> personaPorCi(@RequestParam("ci") String ci) {
        return gestionPersona.buscarPorCi(ci)
                .map(p -> Map.<String, Object>of("existe", true, "persona", gestionPersona.comoSeleccionable(p)))
                .orElse(Map.of("existe", false));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestBody CrearUsuario req) {
        return responder(gestion.crear(req.username(), req.password(), req.personaId(),
                req.persona(), req.rolId(), actor()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> editar(@PathVariable Long id, @RequestBody EditarUsuario req) {
        return responder(gestion.editar(id, req.username(), req.personaId(), req.rolId(), actor()));
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<Map<String, Object>> password(@PathVariable Long id, @RequestBody PasswordReq req) {
        return responder(gestion.cambiarPassword(id, req.password(), actor()));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<Map<String, Object>> estado(@PathVariable Long id, @RequestBody EstadoReq req) {
        return responder(gestion.cambiarEstado(id, Boolean.TRUE.equals(req.activo()), actor()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> eliminar(@PathVariable Long id) {
        return responder(gestion.eliminar(id, actor()));
    }

    public record CrearUsuario(String username, String password, Long personaId,
                               GestionPersonaService.Datos persona, Long rolId) {}
    public record EditarUsuario(String username, Long personaId, Long rolId) {}
    public record PasswordReq(String password) {}
    public record EstadoReq(Boolean activo) {}

    private ResponseEntity<Map<String, Object>> responder(Resultado r) {
        Map<String, Object> cuerpo = (r.usuario() != null)
                ? Map.of("ok", r.ok(), "mensaje", r.mensaje(), "usuario", UsuarioDTO.de(r.usuario()))
                : Map.of("ok", r.ok(), "mensaje", r.mensaje());
        return ResponseEntity.status(r.ok() ? 200 : 400).body(cuerpo);
    }

    private Actor actor() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof JwtUser ju) {
            return new Actor(ju.id(), ju.rol());
        }
        return new Actor(null, null);
    }
}

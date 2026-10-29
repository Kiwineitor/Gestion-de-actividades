package com.usic.uniFex.controller.usuario;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dao.IPuestoDao;
import com.usic.uniFex.model.dao.IUsuarioDao;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.Puesto;
import com.usic.uniFex.model.entity.Usuario;
import com.usic.uniFex.model.service.PresenciaService;
import com.usic.uniFex.model.service.PuestoOcupacionService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/presencia")
@RequiredArgsConstructor
public class PresenciaApiController {

    private final PresenciaService presencia;
    private final IUsuarioDao usuarioDao;
    private final IPuestoDao puestoDao;
    private final PuestoOcupacionService ocupacionService;

    public record Latido(String pantalla, String titulo, String origen,
                         Integer avance, String avancePaso, String avanceFaltan) {
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> latir(@RequestBody(required = false) Latido req) {
        JwtUser u = actual();
        if (u == null) return ResponseEntity.status(401).body(Map.of("ok", false));
        Usuario fila = usuarioDao.findById(u.id()).orElse(null);
        presencia.latir(u.id(), u.username(), nombreDe(fila), u.rol(),
                req == null ? null : req.pantalla(),
                req == null ? null : req.titulo(),
                req == null ? null : req.origen(),
                req == null ? null : req.avance(),
                req == null ? null : req.avancePaso(),
                req == null ? null : req.avanceFaltan());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/salir")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> salir() {
        JwtUser u = actual();
        if (u != null) presencia.olvidar(u.id());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping
    @PreAuthorize(Roles.ADMINISTRA)
    public List<Map<String, Object>> listar() {
        Instant ahora = Instant.now();
        Map<Long, Long> vendidas = ocupacionService.vendidasPorVendedor();
        return presencia.listar().stream().map(p -> {
            List<Puesto> carrito = puestoDao.reservadasPor(p.usuarioId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("usuarioId", p.usuarioId());
            m.put("usuario", p.usuario());
            m.put("nombre", p.nombre());
            m.put("rol", p.rol());
            m.put("pantalla", p.pantalla());
            m.put("titulo", p.titulo());
            m.put("origen", p.origen());
            m.put("enLinea", PresenciaService.estaVivo(p));
            m.put("inactivoSegundos", Math.max(0, ahora.getEpochSecond() - p.visto().getEpochSecond()));
            m.put("casetasEnCarrito", carrito.size());
            m.put("casetas", carrito.stream().map(Puesto::getCodigo).toList());
            m.put("registrando", !carrito.isEmpty());
            m.put("avance", p.avance());
            m.put("avancePaso", p.avancePaso());
            m.put("avanceFaltan", p.avanceFaltan());
            m.put("vendidas", vendidas.getOrDefault(p.usuarioId(), 0L));
            return m;
        }).toList();
    }

    private static String nombreDe(Usuario u) {
        if (u == null) return null;
        Persona p = u.getPersona();
        if (p == null) return u.getUsername();
        String completo = String.join(" ",
                java.util.stream.Stream.of(p.getNombre(), p.getPaterno(), p.getMaterno())
                        .filter(x -> x != null && !x.isBlank()).toList());
        return completo.isBlank() ? u.getUsername() : completo;
    }

    private JwtUser actual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser ju) ? ju : null;
    }
}

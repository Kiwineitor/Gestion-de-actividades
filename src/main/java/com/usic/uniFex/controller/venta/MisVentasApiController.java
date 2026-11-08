package com.usic.uniFex.controller.venta;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.repository.FuncionesInscripcion;
import com.usic.uniFex.security.JwtUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/mis-ventas")
@RequiredArgsConstructor
public class MisVentasApiController {

    private final FuncionesInscripcion funciones;
    private final com.usic.uniFex.model.dao.IInscripcionDao inscripcionDao;

    @GetMapping
    public ResponseEntity<Map<String, Object>> mias(
            @RequestParam(value = "edicion", required = false) Long edicion) {
        Long uid = usuarioActual();
        if (uid == null) return noAutenticado();

        boolean esSuper = esSuperUsuario();
        Long uidParaFuncion = esSuper ? null : uid;

        List<Map<String, Object>> items = funciones.fn_get_inscripciones(uidParaFuncion, edicion);
        if (items == null) items = List.of();

        BigDecimal total = items.stream()
                .map(f -> asBigDecimal(f.get("total_costo")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long inscripciones = items.stream()
                .map(f -> f.get("id_inscripcion"))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();

        List<Map<String, Object>> pendientes = new java.util.ArrayList<>();
        for (Object[] f : inscripcionDao.pendientesPorVenta(esSuper ? null : uid)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ((Number) f[0]).longValue());
            m.put("conComprobante", Boolean.TRUE.equals(f[1]));
            m.put("sinFoto", f[2] == null ? 0 : ((Number) f[2]).intValue());
            pendientes.add(m);
        }

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("cantidad", inscripciones);
        cuerpo.put("total", total);
        cuerpo.put("edicion", edicion);
        cuerpo.put("items", items);
        cuerpo.put("pendientes", pendientes);
        return ResponseEntity.ok(cuerpo);
    }

    @GetMapping("/{idInscripcion}/puestos")
    public ResponseEntity<?> puestos(@PathVariable Long idInscripcion,
            @RequestParam(value = "edicion", required = false) Long edicion) {
        Long uid = usuarioActual();
        if (uid == null) return noAutenticado();

        boolean esMia = funciones.fn_get_inscripciones(uid, edicion).stream()
                .anyMatch(f -> idInscripcion.equals(asLong(f.get("id_inscripcion"))));
        if (!esMia) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "mensaje", "Inscripción no encontrada"));
        }
        return ResponseEntity.ok(funciones.obtener_puestos_por_inscripcion(idInscripcion));
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser ju) ? ju.id() : null;
    }

    private boolean esSuperUsuario() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getAuthorities().stream()
                .anyMatch(auth -> "ROLE_SUPER_USUARIO".equals(auth.getAuthority()));
    }

    private ResponseEntity<Map<String, Object>> noAutenticado() {
        return ResponseEntity.status(401).body(Map.of("ok", false, "mensaje", "No autenticado"));
    }

    private static BigDecimal asBigDecimal(Object o) {
        if (o instanceof BigDecimal b) return b;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private static Long asLong(Object o) {
        return (o instanceof Number n) ? n.longValue() : null;
    }
}

package com.usic.uniFex.controller.venta;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.usic.uniFex.model.service.PuntoVentaService;
import com.usic.uniFex.model.service.RegistroVentaService;
import com.usic.uniFex.security.JwtUser;
import com.usic.uniFex.security.Roles;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/app/punto-venta")
@RequiredArgsConstructor
@PreAuthorize(Roles.VENDE_MESAS)
public class PuntoVentaApiController {

    private final PuntoVentaService puntoVenta;
    private final JdbcTemplate jdbc;

    @GetMapping("/actividades")
    public List<Map<String, Object>> actividades() {
        return jdbc.query("""
                SELECT e.id, e.nombre, e.activa, e.tipo_espacio, e.etiqueta_espacio, e.comprador, e.vende_espacios, e.vende_entradas,
                       e.fechas_texto, e.lugar
                  FROM edicion e
                 WHERE (e.activa OR e.publicacion_independiente) AND NOT e.finalizada AND (e.vende_espacios OR e.vende_entradas)
                 ORDER BY e.activa DESC, e.id DESC
                """, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id"));
            m.put("nombre", rs.getString("nombre"));
            m.put("activa", rs.getBoolean("activa"));
            m.put("tipoEspacio", rs.getString("tipo_espacio"));
            m.put("etiquetaEspacio", rs.getString("etiqueta_espacio"));
            m.put("comprador", rs.getString("comprador"));
            m.put("vendeEspacios", rs.getBoolean("vende_espacios"));
            m.put("vendeEntradas", rs.getBoolean("vende_entradas"));
            m.put("fechasTexto", rs.getString("fechas_texto"));
            m.put("lugar", rs.getString("lugar"));
            return m;
        });
    }

    @PostMapping(value = "/espacios", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> venderEspacios(@RequestPart("datos") PuntoVentaService.VentaEspacios datos,
                                                              @RequestPart(value = "comprobante", required = false) MultipartFile comprobante)
            throws IOException {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        try {
            PuntoVentaService.Resultado r = puntoVenta.venderEspacios(datos, comprobante, usuarioActual());
            cuerpo.put("ok", r.ok());
            cuerpo.put("mensaje", r.mensaje());
            if (r.datos() != null) cuerpo.putAll(r.datos());
            return ResponseEntity.status(r.ok() ? 200 : 409).body(cuerpo);
        } catch (RegistroVentaService.CasetaNoDisponibleException e) {
            cuerpo.put("ok", false);
            cuerpo.put("mensaje", e.getMessage());
            return ResponseEntity.status(409).body(cuerpo);
        }
    }

    private Long usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.getPrincipal() instanceof JwtUser u) ? u.id() : null;
    }
}

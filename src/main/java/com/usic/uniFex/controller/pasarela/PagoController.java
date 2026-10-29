package com.usic.uniFex.controller.pasarela;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.IService.ICategoriaVentaService;
import com.usic.uniFex.model.IService.IPersonaService;
import com.usic.uniFex.model.IService.IVentaBoletoService;
import com.usic.uniFex.model.dto.PagoRequest;
import com.usic.uniFex.model.entity.CategoriaVenta;
import com.usic.uniFex.model.entity.Persona;
import com.usic.uniFex.model.entity.VentaBoleto;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoPasarelaService pasarela;
    private final IPersonaService personaService;
    private final ICategoriaVentaService categoriaVentaService;
    private final IVentaBoletoService ventaBoletoService;

    public PagoController(PagoPasarelaService pasarela,
                          IPersonaService personaService,
                          ICategoriaVentaService categoriaVentaService,
                          IVentaBoletoService ventaBoletoService) {
        this.pasarela = pasarela;
        this.personaService = personaService;
        this.categoriaVentaService = categoriaVentaService;
        this.ventaBoletoService = ventaBoletoService;
    }

    @PostMapping("/crear")
    public ResponseEntity<?> crearPago(@RequestBody PagoRequest request) {
        Map<String, Object> result = pasarela.crearTransaccion(request);
        Object codigoTransaccion = result.get("codigoTransaccion");
        Object urlRedireccion = result.get("urlRedireccion");
        if (codigoTransaccion == null || urlRedireccion == null) {
            throw new IllegalStateException("La pasarela respondio sin codigoTransaccion/urlRedireccion");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("codigoTransaccion", codigoTransaccion);
        body.put("urlRedireccion", urlRedireccion);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/notificacion")
    @Transactional
    public ResponseEntity<?> notificacion(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        String estado = String.valueOf(payload.get("estado"));
        if (!"APROBADO".equalsIgnoreCase(estado)) {
            return ResponseEntity.ok(Map.of("ok", true));
        }

        Map<String, Object> metadata = (Map<String, Object>) payload.get("metadata");
        if (metadata == null) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Sin metadata"));
        }

        String ci       = String.valueOf(metadata.get("ci"));
        String nombre   = String.valueOf(metadata.get("nombre"));
        String paterno  = String.valueOf(metadata.get("paterno"));
        String materno  = String.valueOf(metadata.get("materno"));
        String celular  = String.valueOf(metadata.get("celular"));
        Persona persona = personaService.findFirstByCi(ci).orElseGet(Persona::new);
        persona.setNombre(nombre);
        persona.setPaterno(paterno);
        persona.setMaterno((materno == null || "null".equalsIgnoreCase(materno)) ? null : materno);
        persona.setCi(ci);
        persona.setCelular(celular);
        try { persona.setEstado("CLIENTE"); } catch (Exception ignored) {}
        persona = personaService.save(persona);

        Long categoriaId = Long.valueOf(String.valueOf(metadata.get("categoriaId")));
        CategoriaVenta cat = categoriaVentaService.findById(categoriaId);

        java.math.BigDecimal unit;
        try { unit = new java.math.BigDecimal(cat.getPrecio()); } catch (Exception e) { unit = java.math.BigDecimal.ZERO; }

        int qty = Integer.parseInt(String.valueOf(metadata.get("cantidad")));
        qty = Math.max(1, Math.min(qty, 50));
        java.math.BigDecimal total = unit.multiply(java.math.BigDecimal.valueOf(qty));

        VentaBoleto v = new VentaBoleto();
        v.setPersona(persona);
        v.setCategoria(cat);
        v.setCantidad(qty);
        v.setPrecioUnitario(unit);
        v.setTotal(total);
        v.setFechaHora(java.time.LocalDateTime.now());
        v.setEstado("ACTIVO");
        ventaBoletoService.save(v);

        return ResponseEntity.ok(Map.of("ok", true, "ventaId", v.getId()));
    }
}

package com.usic.uniFex.controller.publico;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dto.CredencialDTO;
import com.usic.uniFex.model.entity.PersonalApoyo;
import com.usic.uniFex.model.IService.IPersonalApoyoService;
import com.usic.uniFex.model.service.ApoyoCodigoService;
import com.usic.uniFex.model.service.CredencialService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/publico/credencial")
@RequiredArgsConstructor
@Slf4j
public class CredencialPublicaController {

    private final CredencialService credenciales;
    private final ApoyoCodigoService codigosApoyo;
    private final IPersonalApoyoService apoyo;

    @GetMapping("/{codigo}")
    public ResponseEntity<Map<String, Object>> ver(@PathVariable String codigo) {
        if (codigo != null && codigo.trim().toUpperCase().startsWith("FXA")) {
            return verApoyo(codigo);
        }
        CredencialDTO c = credenciales.porCodigo(codigo).orElse(null);
        if (c == null) {
            log.info("Credencial consultada con codigo no valido: {}", codigo);
            return ResponseEntity.status(404).body(Map.of(
                    "valida", false,
                    "mensaje", "Esta credencial no es válida"));
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("valida", true);
        m.put("nombre", c.nombre());
        m.put("ci", c.ci());
        m.put("fotoUrl", c.fotoUrl());
        m.put("esTitular", c.esTitular());
        m.put("entidad", c.entidad());
        m.put("rubro", c.rubro());
        m.put("categoria", c.categoria());
        m.put("casetas", c.casetas());
        return ResponseEntity.ok(m);
    }

    private ResponseEntity<Map<String, Object>> verApoyo(String codigo) {
        Long apoyoId = codigosApoyo.apoyoDe(codigo);
        PersonalApoyo p = apoyoId == null ? null : apoyo.findById(apoyoId);
        if (p == null || !"A".equalsIgnoreCase(p.getEstado())) {
            log.info("Credencial de apoyo consultada con codigo no valido: {}", codigo);
            return ResponseEntity.status(404).body(Map.of(
                    "valida", false,
                    "mensaje", "Esta credencial no es válida"));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("valida", true);
        m.put("tipo", "apoyo");
        m.put("nombre", p.getNombreCompleto());
        m.put("ci", p.getCi());
        m.put("dependencia", p.getDependencia() != null ? p.getDependencia().getNombre() : null);
        m.put("rol", p.getRol());
        m.put("tarea", p.getDescripcionTarea());
        return ResponseEntity.ok(m);
    }
}

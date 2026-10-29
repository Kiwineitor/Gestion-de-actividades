package com.usic.uniFex.controller.publico;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.uniFex.model.dto.InteresadoStandRequest;
import com.usic.uniFex.model.service.InteresadoStandEventPublisher;
import com.usic.uniFex.model.service.InteresadoStandService;
import com.usic.uniFex.model.service.InteresadoStandService.Resultado;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/publico/interesados-stand")
@RequiredArgsConstructor
public class InteresadoStandController {

    private final InteresadoStandService interesadoStandService;
    private final InteresadoStandEventPublisher publisher;
    private final JdbcTemplate jdbc;

    @PostMapping
    public ResponseEntity<Map<String, Object>> registrar(@Valid @RequestBody InteresadoStandRequest datos) {
        Boolean abierta = jdbc.query("""
                SELECT e.publica AND e.recibe_interesados FROM categoria c JOIN edicion e ON e.id = c.id_edicion
                 WHERE c.id = ? AND e.activa
                """, rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE, datos.categoriaId());
        if (!Boolean.TRUE.equals(abierta)) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "Esta actividad no está recibiendo solicitudes."));
        }
        Resultado r = interesadoStandService.registrar(datos);
        if (!r.ok()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", r.mensaje()));
        }
        publisher.publicar(r.interesado());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ok", true, "id", r.interesado().getId()));
    }
}

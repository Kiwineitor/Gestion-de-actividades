package com.usic.uniFex.controller.publico;

import java.io.IOException;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> registrar(@Valid @RequestBody InteresadoStandRequest datos) {
        return procesar(datos, null);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> registrarConComprobante(
            @Valid @RequestPart("datos") InteresadoStandRequest datos,
            @RequestPart(value = "comprobante", required = false) MultipartFile comprobante) {
        return procesar(datos, comprobante);
    }

    private ResponseEntity<Map<String, Object>> procesar(InteresadoStandRequest datos, MultipartFile comprobante) {
        Boolean abierta = jdbc.query("""
                SELECT e.publica AND e.recibe_interesados AND NOT e.finalizada
                       AND (e.venta_hasta IS NULL OR e.venta_hasta > now())
                  FROM categoria c JOIN edicion e ON e.id = c.id_edicion
                 WHERE c.id = ? AND (e.activa OR e.publicacion_independiente)
                """, rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE, datos.categoriaId());
        if (!Boolean.TRUE.equals(abierta)) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "Esta actividad ya no está recibiendo compras."));
        }
        Resultado r;
        try {
            r = interesadoStandService.registrar(datos, comprobante);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("ok", false,
                    "mensaje", "Ese puesto acaba de ser tomado por otra persona. Elige otro en el plano."));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", "No se pudo guardar el comprobante. Intenta con otra imagen."));
        }
        if (!r.ok()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "mensaje", r.mensaje()));
        }
        publisher.publicar(r.interesado());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ok", true, "id", r.interesado().getId()));
    }
}
